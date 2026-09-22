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

import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.apache.rocketmq.client.core.RocketMQClientTemplate;
import org.jspecify.annotations.Nullable;
import org.springframework.messaging.Message;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * 绑定到某个实体类主题的发送器,由 {@link RocketMqRegistrar} 按实体类注册.
 * <p>
 * Bean 名为 {@code <实体类简单名首字母小写>RocketMqSender}(例如 {@code User} →
 * {@code userRocketMqSender})。
 * <p>
 * 与 RabbitMQ 模块刻意"只用一个共享模板"不同,RocketMQ 的每条消息都必须带上主题名,所以这里为每个实体类包一层: 使用方注入自己的
 * {@code userRocketMqSender} 后,发送时不必再记住主题名与标签,直接 {@code sender.send(payload)} 即可。
 * <p>
 * 底层仍然是 starter 自动配置的那<b>一个</b> {@link RocketMQClientTemplate}(也就复用同一份 Producer 与连接),
 * 本类只负责把 {@link RocketMqProperties.TopicDefinition#destination()} 补上,不会新建任何客户端。
 * <p>
 * 每个发送器都额外暴露 {@link #consumerGroup()} / {@link #deadLetterTopic()} /
 * {@link #retryTopic()}:死信主题 与重试主题由 Broker 按消费组自动创建,但"名字是什么"只有这里算得出来,使用方据此编写死信补偿监听。
 */
public class RocketMqSender {

	private final RocketMQClientTemplate template;

	private final RocketMqProperties.TopicDefinition definition;

	/**
	 * 创建一个绑定到指定主题的发送器.
	 * @param template starter 自动配置的共享模板
	 * @param definition 该实体类解析后的主题定义
	 */
	public RocketMqSender(RocketMQClientTemplate template, RocketMqProperties.TopicDefinition definition) {
		this.template = Objects.requireNonNull(template, "template must not be null");
		this.definition = Objects.requireNonNull(definition, "definition must not be null");
	}

	/**
	 * 该发送器绑定的主主题名.
	 * @return 主主题名
	 */
	public String topic() {
		return this.definition.topic();
	}

	/**
	 * 该实体类的消费组名.
	 * @return 消费组名
	 */
	public String consumerGroup() {
		return this.definition.consumerGroup();
	}

	/**
	 * 该消费组对应的死信主题名,即 RocketMQ 的 {@code %DLQ%<消费组>}.
	 * @return 死信主题名
	 */
	public String deadLetterTopic() {
		return this.definition.deadLetterTopic();
	}

	/**
	 * 该消费组对应的重试主题名,即 RocketMQ 的 {@code %RETRY%<消费组>}.
	 * @return 重试主题名
	 */
	public String retryTopic() {
		return this.definition.retryTopic();
	}

	/**
	 * 同步发送一条普通消息到该实体类的主主题.
	 * @param payload 消息体,由模板的消息转换器序列化
	 * @return 发送回执
	 */
	public SendReceipt send(Object payload) {
		return this.template.syncSendNormalMessage(this.definition.destination(), payload);
	}

	/**
	 * 同步发送一条自带消息头的消息到该实体类的主主题.
	 * @param message spring messaging 消息
	 * @return 发送回执
	 */
	public SendReceipt send(Message<?> message) {
		return this.template.syncSendNormalMessage(this.definition.destination(), message);
	}

	/**
	 * 同步发送一条延时消息到该实体类的主主题.
	 * @param payload 消息体
	 * @param delay 延时时间
	 * @return 发送回执
	 */
	public SendReceipt sendDelay(Object payload, Duration delay) {
		return this.template.syncSendDelayMessage(this.definition.destination(), payload, delay);
	}

	/**
	 * 异步发送一条普通消息到该实体类的主主题.
	 * @param payload 消息体
	 * @param future 需要一并完成的调用方 future,可为 {@code null},此时只返回框架自己的 future
	 * @return 发送结果的 future
	 */
	public CompletableFuture<SendReceipt> sendAsync(Object payload, @Nullable CompletableFuture<SendReceipt> future) {
		return this.template.asyncSendNormalMessage(this.definition.destination(), payload, future);
	}

}
