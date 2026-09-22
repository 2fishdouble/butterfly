/*
 * Copyright 2012-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.butterfly.redis.autoconfigure;

import io.github.butterfly.autoconfigure.SpelSup;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 覆盖幂等切面的注册条件与让位规则.
 */
class ButterflyRedisConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ButterflyRedisConfiguration.class))
		.withBean(RedissonClient.class, ButterflyRedisConfigurationTests::redissonClient)
		.withBean(SpelSup.class, () -> new SpelSup(new DefaultListableBeanFactory()));

	@Test
	void registersIdempotentAspect() {
		this.contextRunner.run((context) -> assertThat(context).hasSingleBean(IdempotentAspect.class));
	}

	@Test
	void backsOffWhenUserDefinesAspect() {
		IdempotentAspect custom = new IdempotentAspect(new SpelSup(new DefaultListableBeanFactory()), redissonClient());

		this.contextRunner.withBean(IdempotentAspect.class, () -> custom).run((context) -> {
			assertThat(context).hasSingleBean(IdempotentAspect.class);
			assertThat(context.getBean(IdempotentAspect.class)).isSameAs(custom);
		});
	}

	@Test
	void skipsAspectWhenAspectJIsMissing() {
		this.contextRunner.withClassLoader(new FilteredClassLoader("org.aspectj.lang"))
			.run((context) -> assertThat(context).doesNotHaveBean(IdempotentAspect.class));
	}

	@Test
	void skipsWholeAutoConfigurationWhenRedissonIsMissing() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ButterflyRedisConfiguration.class))
			.withClassLoader(new FilteredClassLoader(RedissonClient.class))
			.run((context) -> assertThat(context).doesNotHaveBean(IdempotentAspect.class));
	}

	private static RedissonClient redissonClient() {
		return (RedissonClient) Proxy.newProxyInstance(RedissonClient.class.getClassLoader(),
				new Class<?>[] { RedissonClient.class }, (proxy, method, args) -> null);
	}

}
