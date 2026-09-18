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
import org.springframework.amqp.core.AcknowledgeMode;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 校验 {@link RabbitMqProperties} 的解析规则:内置默认值、全局覆盖与实体类覆盖的三层回退,以及非法值的快速失败.
 */
class RabbitMqPropertiesTests {

	private final RabbitMqProperties properties = new RabbitMqProperties();

	/**
	 * 一项都不配置时,所有字段都必须有具体值,不能留空等使用方兜底.
	 */
	@Test
	void resolvesEveryFieldToBuiltInDefaults() {
		RabbitMqProperties.EntityDefinition definition = this.properties.resolve(Computer.class);

		assertThat(definition.exchange()).isEqualTo("computer");
		assertThat(definition.queue()).isEqualTo("computer");
		assertThat(definition.routingKey()).isEqualTo("computer");
		assertThat(definition.exchangeType()).isEqualTo(RabbitMqProperties.ExchangeType.TOPIC);
		assertThat(definition.durable()).isTrue();
		assertThat(definition.autoDelete()).isFalse();

		assertThat(definition.deadLetterEnabled()).isTrue();
		assertThat(definition.deadLetterExchange()).isEqualTo("computer.dlx");
		assertThat(definition.deadLetterQueue()).isEqualTo("computer.dlq");
		assertThat(definition.deadLetterRoutingKey()).isEqualTo("computer.dead");

		assertThat(definition.delayEnabled()).isFalse();
		assertThat(definition.delayExchange()).isEqualTo("computer.delay");
		assertThat(definition.delayQueue()).isEqualTo("computer.delay");
		assertThat(definition.delayRoutingKey()).isEqualTo("computer.delay");
		assertThat(definition.delayTtl()).isEqualTo(Duration.ofSeconds(30));

		assertThat(definition.attempts()).isEqualTo(3);
		assertThat(definition.retryDelay()).isEqualTo(Duration.ofSeconds(1));
		assertThat(definition.retryMultiplier()).isEqualTo(2.0);
		assertThat(definition.retryMaxDelay()).isEqualTo(Duration.ofSeconds(30));

		assertThat(definition.concurrency()).isEqualTo(1);
		assertThat(definition.prefetch()).isEqualTo(1);
		assertThat(definition.requeueRejected()).isFalse();
		assertThat(definition.acknowledgeMode()).isEqualTo(AcknowledgeMode.MANUAL);
	}

	/**
	 * 全局配置覆盖内置默认值,且对所有实体类生效.
	 */
	@Test
	void globalConfigurationOverridesBuiltInDefaults() {
		this.properties.setExchangeType(RabbitMqProperties.ExchangeType.DIRECT);
		this.properties.setDurable(false);
		this.properties.setAutoDelete(true);
		this.properties.getRetry().setAttempts(5);
		this.properties.getRetry().setDelay(Duration.ofMillis(250));
		this.properties.getRetry().setMultiplier(3.0);
		this.properties.getRetry().setMaxDelay(Duration.ofSeconds(5));
		this.properties.getDeadLetter().setEnabled(false);
		this.properties.getDelay().setEnabled(true);
		this.properties.getDelay().setTtl(Duration.ofSeconds(2));
		this.properties.getListener().setConcurrency(4);
		this.properties.getListener().setPrefetch(10);
		this.properties.getListener().setRequeueRejected(true);
		this.properties.getListener().setAcknowledgeMode(AcknowledgeMode.AUTO);

		for (Class<?> entityClass : new Class<?>[] { Computer.class, Order.class }) {
			RabbitMqProperties.EntityDefinition definition = this.properties.resolve(entityClass);

			assertThat(definition.exchangeType()).isEqualTo(RabbitMqProperties.ExchangeType.DIRECT);
			assertThat(definition.durable()).isFalse();
			assertThat(definition.autoDelete()).isTrue();
			assertThat(definition.deadLetterEnabled()).isFalse();
			assertThat(definition.delayEnabled()).isTrue();
			assertThat(definition.delayTtl()).isEqualTo(Duration.ofSeconds(2));
			assertThat(definition.attempts()).isEqualTo(5);
			assertThat(definition.retryDelay()).isEqualTo(Duration.ofMillis(250));
			assertThat(definition.retryMultiplier()).isEqualTo(3.0);
			assertThat(definition.retryMaxDelay()).isEqualTo(Duration.ofSeconds(5));
			assertThat(definition.concurrency()).isEqualTo(4);
			assertThat(definition.prefetch()).isEqualTo(10);
			assertThat(definition.requeueRejected()).isTrue();
			assertThat(definition.acknowledgeMode()).isEqualTo(AcknowledgeMode.AUTO);
		}
	}

	/**
	 * 实体类覆盖优先于全局配置,且只覆盖写了的字段,其余仍回落到全局值;派生名称跟随覆盖后的主名称,而不是实体类名.
	 */
	@Test
	void entityOverrideWinsOverGlobalAndFallsBackPerField() {
		this.properties.getRetry().setAttempts(5);
		this.properties.getRetry().setDelay(Duration.ofSeconds(2));
		this.properties.getDeadLetter().setQueue("global-dlq");

		RabbitMqProperties.Entity entity = new RabbitMqProperties.Entity();
		entity.setExchange("butterfly-computer");
		entity.setQueue("butterfly-computer-queue");
		entity.setRoutingKey("computer.created");
		entity.setRetry(new RabbitMqProperties.Retry());
		entity.getRetry().setAttempts(7);
		this.properties.setEntities(entities("computer", entity));

		RabbitMqProperties.EntityDefinition definition = this.properties.resolve(Computer.class);

		assertThat(definition.exchange()).isEqualTo("butterfly-computer");
		assertThat(definition.queue()).isEqualTo("butterfly-computer-queue");
		assertThat(definition.routingKey()).isEqualTo("computer.created");
		assertThat(definition.attempts()).isEqualTo(7);
		assertThat(definition.retryDelay()).isEqualTo(Duration.ofSeconds(2));
		assertThat(definition.deadLetterExchange()).isEqualTo("butterfly-computer.dlx");
		assertThat(definition.deadLetterQueue()).isEqualTo("global-dlq");
		assertThat(definition.deadLetterRoutingKey()).isEqualTo("computer.created.dead");

		assertThat(this.properties.resolve(Order.class).exchange()).isEqualTo("order");
	}

	/**
	 * 实体类 key 忽略大小写,写 {@code Computer} 或 {@code COMPUTER} 都应命中.
	 */
	@Test
	void entityKeyIsCaseInsensitive() {
		RabbitMqProperties.Entity entity = new RabbitMqProperties.Entity();
		entity.setQueue("computer-from-uppercase");
		this.properties.setEntities(entities("COMPUTER", entity));

		assertThat(this.properties.resolve(Computer.class).queue()).isEqualTo("computer-from-uppercase");
	}

	/**
	 * 完全没写 key 的实体类回落到内置默认值,不会因为 {@code entities} 非空而受影响.
	 */
	@Test
	void unconfiguredEntityStillResolvesToDefaults() {
		RabbitMqProperties.Entity entity = new RabbitMqProperties.Entity();
		entity.setQueue("computer-queue");
		this.properties.setEntities(entities("computer", entity));

		assertThat(this.properties.resolve(Order.class).queue()).isEqualTo("order");
	}

	/**
	 * 重试次数必须为正数,配置为 {@code 0} 时立刻失败而不是让监听容器静默不重试.
	 */
	@Test
	void nonPositiveAttemptsFailsFast() {
		this.properties.getRetry().setAttempts(0);

		assertThatThrownBy(() -> this.properties.resolve(Computer.class)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("attempts")
			.hasMessageContaining(Computer.class.getName());
	}

	/**
	 * 实体类级覆盖同样要校验.
	 */
	@Test
	void nonPositiveEntityAttemptsFailsFast() {
		RabbitMqProperties.Entity entity = new RabbitMqProperties.Entity();
		entity.setRetry(new RabbitMqProperties.Retry());
		entity.getRetry().setAttempts(-1);
		this.properties.setEntities(entities("computer", entity));

		assertThatThrownBy(() -> this.properties.resolve(Computer.class)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("attempts");
	}

	/**
	 * 开启延时时 TTL 必须为正数.
	 */
	@Test
	void nonPositiveDelayTtlFailsFastWhenDelayIsEnabled() {
		this.properties.getDelay().setEnabled(true);
		this.properties.getDelay().setTtl(Duration.ZERO);

		assertThatThrownBy(() -> this.properties.resolve(Computer.class)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("delay.ttl");
	}

	/**
	 * 延时关闭时 TTL 不参与任何计算,因此不必校验,免得留一个用不上的配置把应用卡在启动阶段.
	 */
	@Test
	void delayTtlIsNotValidatedWhileDelayIsDisabled() {
		this.properties.getDelay().setTtl(Duration.ofSeconds(-1));

		assertThatCode(() -> this.properties.resolve(Computer.class)).doesNotThrowAnyException();
	}

	private Map<String, RabbitMqProperties.Entity> entities(String key, RabbitMqProperties.Entity entity) {
		Map<String, RabbitMqProperties.Entity> entities = new LinkedHashMap<>();
		entities.put(key, entity);
		return entities;
	}

	static class Computer {

	}

	static class Order {

	}

}
