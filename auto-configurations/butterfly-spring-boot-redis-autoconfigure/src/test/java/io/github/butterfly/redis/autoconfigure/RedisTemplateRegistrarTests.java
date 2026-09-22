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

import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.ResolvableType;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 覆盖 {@link EnableRedisTemplates} 的 Bean 定义注册:命名、泛型目标、序列化器与跳过规则.
 */
class RedisTemplateRegistrarTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withBean(RedisConnectionFactory.class, RedisTemplateRegistrarTests::connectionFactory);

	@Test
	void registersTemplatePerEntityClassWithStringKeyAndJsonValueSerializers() {
		this.contextRunner.withUserConfiguration(TemplateConfiguration.class).run((context) -> {
			assertThat(context).hasBean("sampleEntityRedisTemplate");

			RedisTemplate<?, ?> template = context.getBean("sampleEntityRedisTemplate", RedisTemplate.class);

			assertThat(template.getKeySerializer()).isInstanceOf(StringRedisSerializer.class);
			assertThat(template.getHashKeySerializer()).isInstanceOf(StringRedisSerializer.class);
			assertThat(template.getValueSerializer()).isInstanceOf(JacksonJsonRedisSerializer.class);
			assertThat(template.getHashValueSerializer()).isInstanceOf(JacksonJsonRedisSerializer.class);
		});
	}

	@Test
	void registersTemplateWithEntityAsValueGenericType() {
		this.contextRunner.withUserConfiguration(TemplateConfiguration.class).run((context) -> {
			ResolvableType type = ResolvableType.forClassWithGenerics(RedisTemplate.class, String.class,
					SampleEntity.class);

			assertThat(context.getBeanProvider(type).getObject()).isInstanceOf(RedisTemplate.class);
		});
	}

	@Test
	void skipsEntityWhenBeanNameIsAlreadyRegistered() {
		RedisTemplate<String, SampleEntity> existing = new RedisTemplate<>();
		existing.setConnectionFactory(connectionFactory());

		this.contextRunner.withUserConfiguration(TemplateConfiguration.class)
			.withBean("sampleEntityRedisTemplate", RedisTemplate.class, () -> existing)
			.run((context) -> assertThat(context.getBean("sampleEntityRedisTemplate")).isSameAs(existing));
	}

	@Test
	void registersNothingWhenAnnotationHasNoValue() {
		this.contextRunner.withUserConfiguration(EmptyConfiguration.class)
			.run((context) -> assertThat(context.getBeanNamesForType(RedisTemplate.class)).isEmpty());
	}

	@Test
	void registersNothingWhenAnnotationIsMissing() {
		this.contextRunner.withUserConfiguration(PlainImportConfiguration.class)
			.run((context) -> assertThat(context.getBeanNamesForType(RedisTemplate.class)).isEmpty());
	}

	private static RedisConnectionFactory connectionFactory() {
		return (RedisConnectionFactory) Proxy.newProxyInstance(RedisConnectionFactory.class.getClassLoader(),
				new Class<?>[] { RedisConnectionFactory.class }, (proxy, method, args) -> null);
	}

	@Configuration(proxyBeanMethods = false)
	@EnableRedisTemplates(SampleEntity.class)
	static class TemplateConfiguration {

	}

	@Configuration(proxyBeanMethods = false)
	@EnableRedisTemplates
	static class EmptyConfiguration {

	}

	@Configuration(proxyBeanMethods = false)
	@Import(RedisTemplateRegistrar.class)
	static class PlainImportConfiguration {

	}

	@Setter
	@Getter
	static class SampleEntity {

		private String name;

	}

}
