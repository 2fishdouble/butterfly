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

package io.github.butterfly.kafka.autoconfigure;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ContainerCustomizer;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 校验 {@link ButterflyKafkaAutoConfiguration} 补齐消费端默认值,且不覆盖使用方的显式配置,全程不触网.
 */
class ButterflyKafkaAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration.class, ButterflyKafkaAutoConfiguration.class))
		.withPropertyValues("spring.kafka.bootstrap-servers=localhost:9092");

	@Test
	void fillsConsumerDefaultsFromApplicationName() {
		this.contextRunner.withPropertyValues("spring.application.name=order-service").run((context) -> {
			DefaultKafkaConsumerFactory<?, ?> factory = consumerFactory(context);

			assertThat(factory.getConfigurationProperties())
				.containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "order-service")
				.containsEntry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
				.containsEntry(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JacksonJsonDeserializer.class)
				.containsEntry(JacksonJsonDeserializer.TRUSTED_PACKAGES, "*");
		});
	}

	@Test
	void groupIdFallsBackWhenApplicationNameMissing() {
		this.contextRunner.run((context) -> assertThat(consumerFactory(context).getConfigurationProperties())
			.containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "butterfly"));
	}

	/**
	 * 使用方显式配置过的三项都不能被默认值顶掉。
	 */
	@Test
	void explicitConsumerConfigurationIsNotOverridden() {
		this.contextRunner
			.withPropertyValues("spring.kafka.consumer.group-id=my-group",
					"spring.kafka.consumer.auto-offset-reset=latest",
					"spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer")
			.run((context) -> {
				DefaultKafkaConsumerFactory<?, ?> factory = consumerFactory(context);

				// 使用方配过 value 反序列化器,框架就不能再声明自己的 JSON 反序列化器与信任包
				assertThat(factory.getConfigurationProperties())
					.containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "my-group")
					.containsEntry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
					.doesNotContainKey(JacksonJsonDeserializer.TRUSTED_PACKAGES);
				assertThat(factory.getConfigurationProperties().get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG))
					.isIn(StringDeserializer.class, StringDeserializer.class.getName());
			});
	}

	@Test
	void listenerContainerDefaultsToManualAck() {
		this.contextRunner.run((context) -> {
			ConcurrentMessageListenerContainer<Object, Object> container = newListenerContainer(context);
			containerCustomizer(context).configure(container);

			assertThat(container.getContainerProperties().getAckMode()).isEqualTo(ContainerProperties.AckMode.MANUAL);
		});
	}

	@Test
	void explicitAckModeIsNotOverridden() {
		this.contextRunner.withPropertyValues("spring.kafka.listener.ack-mode=record").run((context) -> {
			ConcurrentMessageListenerContainer<Object, Object> container = newListenerContainer(context);
			container.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);

			containerCustomizer(context).configure(container);

			assertThat(container.getContainerProperties().getAckMode()).isEqualTo(ContainerProperties.AckMode.RECORD);
		});
	}

	/**
	 * {@link ContainerCustomizer} 在 Boot 里是单例扩展点,使用方自定义时本配置必须退让,否则会 Bean 冲突。
	 */
	@Test
	void userContainerCustomizerTakesPrecedence() {
		this.contextRunner.withUserConfiguration(UserContainerCustomizerConfiguration.class).run((context) -> {
			assertThat(context).hasSingleBean(ContainerCustomizer.class);
			assertThat(context).doesNotHaveBean("butterflyManualAckContainerCustomizer");
		});
	}

	private static DefaultKafkaConsumerFactory<?, ?> consumerFactory(ApplicationContext context) {
		return context.getBean(DefaultKafkaConsumerFactory.class);
	}

	@SuppressWarnings("unchecked")
	private static ContainerCustomizer<Object, Object, ConcurrentMessageListenerContainer<Object, Object>> containerCustomizer(
			ApplicationContext context) {
		return context.getBean(ContainerCustomizer.class);
	}

	@SuppressWarnings("unchecked")
	private static ConcurrentMessageListenerContainer<Object, Object> newListenerContainer(ApplicationContext context) {
		ConsumerFactory<Object, Object> consumerFactory = (ConsumerFactory<Object, Object>) context
			.getBean(ConsumerFactory.class);
		return new ConcurrentMessageListenerContainer<>(consumerFactory,
				new ContainerProperties("butterfly-autoconfigure-test"));
	}

	@Configuration(proxyBeanMethods = false)
	static class UserContainerCustomizerConfiguration {

		@Bean
		ContainerCustomizer<Object, Object, ConcurrentMessageListenerContainer<Object, Object>> myContainerCustomizer() {
			return (container) -> container.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
		}

	}

}
