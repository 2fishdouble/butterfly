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
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 校验 {@link RabbitMqTemplate} 的交换机/路由键解析规则,全程不触网.
 */
class RabbitMqTemplateTests {

	private static final String DEFAULT_EXCHANGE = "butterfly.topic";

	private static final String DEFAULT_ROUTING_KEY = "butterfly.default";

	private final RecordingRabbitTemplate rabbitTemplate = new RecordingRabbitTemplate();

	@Test
	void sendUsesDefaultExchangeAndRoutingKey() {
		RabbitMqTemplate template = new RabbitMqTemplate(this.rabbitTemplate, DEFAULT_EXCHANGE, DEFAULT_ROUTING_KEY);

		template.send("payload");

		assertThat(this.rabbitTemplate.exchange).isEqualTo(DEFAULT_EXCHANGE);
		assertThat(this.rabbitTemplate.routingKey).isEqualTo(DEFAULT_ROUTING_KEY);
		assertThat(this.rabbitTemplate.payload).isEqualTo("payload");
	}

	@Test
	void sendOverridesRoutingKeyButKeepsDefaultExchange() {
		RabbitMqTemplate template = new RabbitMqTemplate(this.rabbitTemplate, DEFAULT_EXCHANGE, DEFAULT_ROUTING_KEY);

		template.send("butterfly.other", "payload");

		assertThat(this.rabbitTemplate.exchange).isEqualTo(DEFAULT_EXCHANGE);
		assertThat(this.rabbitTemplate.routingKey).isEqualTo("butterfly.other");
	}

	@Test
	void sendFallsBackToDefaultExchangeWhenExchangeIsBlank() {
		RabbitMqTemplate template = new RabbitMqTemplate(this.rabbitTemplate, DEFAULT_EXCHANGE, DEFAULT_ROUTING_KEY);

		template.send("", "butterfly.other", "payload");

		assertThat(this.rabbitTemplate.exchange).isEqualTo(DEFAULT_EXCHANGE);
		assertThat(this.rabbitTemplate.routingKey).isEqualTo("butterfly.other");
	}

	@Test
	void sendUsesAmqpDefaultExchangeWhenNothingConfigured() {
		RabbitMqTemplate template = new RabbitMqTemplate(this.rabbitTemplate, null, DEFAULT_ROUTING_KEY);

		template.send("queue.name", "payload");

		assertThat(this.rabbitTemplate.exchange).isEmpty();
		assertThat(this.rabbitTemplate.routingKey).isEqualTo("queue.name");
	}

	@Test
	void sendAndReceiveReturnsReply() {
		this.rabbitTemplate.reply = "pong";
		RabbitMqTemplate template = new RabbitMqTemplate(this.rabbitTemplate, DEFAULT_EXCHANGE, DEFAULT_ROUTING_KEY);

		assertThat(template.sendAndReceive("butterfly.rpc", "ping")).isEqualTo("pong");
		assertThat(this.rabbitTemplate.exchange).isEqualTo(DEFAULT_EXCHANGE);
		assertThat(this.rabbitTemplate.routingKey).isEqualTo("butterfly.rpc");
	}

	@Test
	void rejectsMissingRoutingKey() {
		RabbitMqTemplate template = new RabbitMqTemplate(this.rabbitTemplate, DEFAULT_EXCHANGE, null);

		assertThatThrownBy(() -> template.send("payload")).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("routingKey");
	}

	@Test
	void rejectsNullRabbitTemplate() {
		assertThatThrownBy(() -> new RabbitMqTemplate(null, DEFAULT_EXCHANGE, DEFAULT_ROUTING_KEY))
			.isInstanceOf(NullPointerException.class);
	}

	/**
	 * 记录调用参数的 {@link RabbitTemplate} 测试替身,避免引入 Mockito 等需要字节码增强的依赖.
	 */
	private static final class RecordingRabbitTemplate extends RabbitTemplate {

		private String exchange;

		private String routingKey;

		private Object payload;

		private Object reply;

		@Override
		public void convertAndSend(String exchange, String routingKey, Object object) {
			record(exchange, routingKey, object);
		}

		@Override
		public Object convertSendAndReceive(String exchange, String routingKey, Object object) {
			record(exchange, routingKey, object);
			return this.reply;
		}

		private void record(String exchange, String routingKey, Object payload) {
			this.exchange = exchange;
			this.routingKey = routingKey;
			this.payload = payload;
		}

	}

}
