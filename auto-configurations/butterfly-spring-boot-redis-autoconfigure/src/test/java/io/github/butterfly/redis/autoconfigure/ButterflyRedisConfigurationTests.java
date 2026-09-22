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
