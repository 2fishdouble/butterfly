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

package io.github.butterfly.rocketmq.autoconfigure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * 校验 {@link RocketMqProperties} 的默认值、实体类级覆盖与非法配置的快速失败:一个实体类对应一个主题、一个消费组,以及
 * 由消费组推导出的死信主题与重试主题。全程不触网。
 */
class RocketMqPropertiesTests {

	/**
	 * 什么都不配时:主题名与消费组名取实体类简单名首字母小写,死信主题是 RocketMQ 的 {@code %DLQ%<消费组>}.
	 */
	@Test
	void resolvesBuiltInDefaults() {
		RocketMqProperties properties = new RocketMqProperties();

		RocketMqProperties.TopicDefinition definition = properties.resolve(Computer.class);

		assertThat(definition.topic()).isEqualTo("computer");
		assertThat(definition.consumerGroup()).isEqualTo("computer");
		assertThat(definition.deadLetterTopic()).isEqualTo("%DLQ%computer");
		assertThat(definition.retryTopic()).isEqualTo("%RETRY%computer");
		assertThat(definition.tag()).isEqualTo("*");
		assertThat(definition.queueCount()).isEqualTo(4);
		assertThat(definition.createTopic()).isTrue();
	}

	/**
	 * 没有标签(或标签为 {@code *})时发送目的地就是主题名本身,不要拼出一个 {@code topic:*}.
	 */
	@Test
	void destinationOmitsWildcardTag() {
		RocketMqProperties properties = new RocketMqProperties();

		assertThat(properties.resolve(Computer.class).destination()).isEqualTo("computer");

		properties.setTag("created");
		assertThat(properties.resolve(Computer.class).destination()).isEqualTo("computer:created");
	}

	/**
	 * 全局前缀只影响消费组,不影响主题名;死信与重试主题跟着消费组一起改.
	 */
	@Test
	void consumerGroupPrefixOnlyAffectsConsumerGroup() {
		RocketMqProperties properties = new RocketMqProperties();
		properties.setConsumerGroupPrefix("butterfly-");

		RocketMqProperties.TopicDefinition definition = properties.resolve(Computer.class);

		assertThat(definition.topic()).isEqualTo("computer");
		assertThat(definition.consumerGroup()).isEqualTo("butterfly-computer");
		assertThat(definition.deadLetterTopic()).isEqualTo("%DLQ%butterfly-computer");
		assertThat(definition.retryTopic()).isEqualTo("%RETRY%butterfly-computer");
	}

	/**
	 * 实体类级覆盖优先于全局默认,且未覆盖的实体类仍沿用全局默认.
	 */
	@Test
	void entityOverridesWinOverGlobals() {
		RocketMqProperties properties = new RocketMqProperties();
		properties.setQueueCount(2);
		properties.setTag("global-tag");

		RocketMqProperties.Entity computer = new RocketMqProperties.Entity();
		computer.setTopic("butterfly-computer");
		computer.setConsumerGroup("butterfly-computer-group");
		computer.setQueueCount(8);
		computer.setCreateTopic(false);
		properties.getEntities().put("Computer", computer);

		RocketMqProperties.TopicDefinition overridden = properties.resolve(Computer.class);
		assertThat(overridden.topic()).isEqualTo("butterfly-computer");
		assertThat(overridden.consumerGroup()).isEqualTo("butterfly-computer-group");
		// 消费组被覆盖后,死信与重试主题必须跟着新消费组走
		assertThat(overridden.deadLetterTopic()).isEqualTo("%DLQ%butterfly-computer-group");
		assertThat(overridden.retryTopic()).isEqualTo("%RETRY%butterfly-computer-group");
		assertThat(overridden.tag()).isEqualTo("global-tag");
		assertThat(overridden.queueCount()).isEqualTo(8);
		assertThat(overridden.createTopic()).isFalse();

		RocketMqProperties.TopicDefinition untouched = properties.resolve(Order.class);
		assertThat(untouched.topic()).isEqualTo("order");
		assertThat(untouched.consumerGroup()).isEqualTo("order");
		assertThat(untouched.queueCount()).isEqualTo(2);
		assertThat(untouched.createTopic()).isTrue();
	}

	/**
	 * 死信主题与重试主题也可以直接改名,便于对接已有的运维命名.
	 */
	@Test
	void deadLetterAndRetryTopicCanBeRenamed() {
		RocketMqProperties properties = new RocketMqProperties();
		RocketMqProperties.Entity computer = new RocketMqProperties.Entity();
		computer.setDeadLetterTopic("computer-dead-letter");
		computer.setRetryTopic("computer-retry");
		properties.getEntities().put("computer", computer);

		RocketMqProperties.TopicDefinition definition = properties.resolve(Computer.class);

		assertThat(definition.deadLetterTopic()).isEqualTo("computer-dead-letter");
		assertThat(definition.retryTopic()).isEqualTo("computer-retry");
	}

	/**
	 * 非法配置要在解析时尽早暴露,而不是等到运行期才发现队列数是 0.
	 */
	@Test
	void invalidQueueCountFailsFast() {
		RocketMqProperties properties = new RocketMqProperties();
		properties.setQueueCount(0);

		assertThatIllegalStateException().isThrownBy(() -> properties.resolve(Computer.class))
			.withMessageContaining("queue-count");
	}

	/**
	 * 空主题名没有任何意义,必须拒绝.
	 */
	@Test
	void blankTopicFailsFast() {
		RocketMqProperties properties = new RocketMqProperties();
		RocketMqProperties.Entity computer = new RocketMqProperties.Entity();
		computer.setTopic("  ");
		properties.getEntities().put("computer", computer);

		assertThatIllegalStateException().isThrownBy(() -> properties.resolve(Computer.class))
			.withMessageContaining("topic");
	}

	/**
	 * 空消费组同样没有意义:死信主题是从消费组推导出来的,推导不出名字就没有死信可谈.
	 */
	@Test
	void blankConsumerGroupFailsFast() {
		RocketMqProperties properties = new RocketMqProperties();
		RocketMqProperties.Entity computer = new RocketMqProperties.Entity();
		computer.setConsumerGroup("");
		properties.getEntities().put("computer", computer);

		assertThatIllegalStateException().isThrownBy(() -> properties.resolve(Computer.class))
			.withMessageContaining("consumer-group");
	}

	static class Computer {

	}

	static class Order {

	}

}
