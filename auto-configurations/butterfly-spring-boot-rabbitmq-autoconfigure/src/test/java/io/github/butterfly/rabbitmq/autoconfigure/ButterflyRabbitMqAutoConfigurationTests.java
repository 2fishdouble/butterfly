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

package io.github.butterfly.rabbitmq.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ButterflyRabbitMqAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ButterflyRabbitMqAutoConfiguration.class));

	@Test
	void rabbitTemplatePresentRegistersRabbitMqTemplate() {
		this.contextRunner.withBean(ConnectionFactory.class, ButterflyRabbitMqAutoConfigurationTests::connectionFactory)
			.withBean(RabbitTemplate.class, ButterflyRabbitMqAutoConfigurationTests::rabbitTemplate)
			.run((context) -> assertThat(context).hasSingleBean(RabbitMqTemplate.class));
	}

	@Test
	void noRabbitTemplateSkipsRabbitMqTemplate() {
		this.contextRunner.run((context) -> assertThat(context).doesNotHaveBean(RabbitMqTemplate.class));
	}

	@Test
	void userDefinedRabbitMqTemplateWins() {
		RabbitMqTemplate custom = new RabbitMqTemplate(rabbitTemplate(), null, "custom");
		this.contextRunner.withBean(ConnectionFactory.class, ButterflyRabbitMqAutoConfigurationTests::connectionFactory)
			.withBean(RabbitTemplate.class, ButterflyRabbitMqAutoConfigurationTests::rabbitTemplate)
			.withBean(RabbitMqTemplate.class, () -> custom)
			.run((context) -> assertThat(context).getBean(RabbitMqTemplate.class).isSameAs(custom));
	}

	@Test
	void propertiesFeedDefaultExchangeAndRoutingKey() {
		this.contextRunner.withBean(ConnectionFactory.class, ButterflyRabbitMqAutoConfigurationTests::connectionFactory)
			.withBean(RabbitTemplate.class, ButterflyRabbitMqAutoConfigurationTests::rabbitTemplate)
			.withPropertyValues("butterfly.rabbitmq.default-exchange=butterfly.topic",
					"butterfly.rabbitmq.default-routing-key=butterfly.default")
			.run((context) -> assertThat(context).getBean(RabbitMqTemplateProperties.class)
				.hasFieldOrPropertyWithValue("defaultExchange", "butterfly.topic")
				.hasFieldOrPropertyWithValue("defaultRoutingKey", "butterfly.default"));
	}

	@Test
	void registersAfterBootRabbitAutoConfiguration() {
		new ApplicationContextRunner()
			.withConfiguration(
					AutoConfigurations.of(RabbitAutoConfiguration.class, ButterflyRabbitMqAutoConfiguration.class))
			.withPropertyValues("spring.rabbitmq.host=localhost")
			.run((context) -> assertThat(context).hasSingleBean(RabbitMqTemplate.class));
	}

	private static ConnectionFactory connectionFactory() {
		return new CachingConnectionFactory("localhost");
	}

	private static RabbitTemplate rabbitTemplate() {
		return new RabbitTemplate(connectionFactory());
	}

}
