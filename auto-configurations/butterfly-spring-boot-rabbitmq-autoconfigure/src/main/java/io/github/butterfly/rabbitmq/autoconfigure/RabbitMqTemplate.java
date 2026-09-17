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

import org.jspecify.annotations.Nullable;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * 基于 {@link RabbitTemplate} 的消息收发门面,在保留原模板全部能力的同时收敛默认交换机与默认路由键.
 * <p>
 * 底层依赖 Spring Boot {@code RabbitAutoConfiguration} 生成的 {@link RabbitTemplate} bean(需配置
 * {@code spring.rabbitmq.*} 或由使用方自行提供连接工厂),因此仅在有可用模板时才被注册。
 * <p>
 * 交换机与路由键的取值优先级为:方法入参 &gt; {@link RabbitMqTemplateProperties} 中的默认值;两者皆空时, 交换机退化为
 * RabbitMQ 内置默认交换机(空字符串),而路由键无法推断,直接抛出 {@link IllegalStateException}。
 */
public final class RabbitMqTemplate {

	private static final String DEFAULT_EXCHANGE = "";

	private final RabbitTemplate rabbitTemplate;

	private final @Nullable String defaultExchange;

	private final @Nullable String defaultRoutingKey;

	/**
	 * 构造门面实例.
	 * @param rabbitTemplate 真正执行收发的 Rabbit 模板,不可为 {@code null}
	 * @param defaultExchange 默认交换机,可为空;为空时使用 RabbitMQ 内置默认交换机
	 * @param defaultRoutingKey 默认路由键,可为空;为空时要求调用方显式传入路由键
	 */
	public RabbitMqTemplate(RabbitTemplate rabbitTemplate, @Nullable String defaultExchange,
			@Nullable String defaultRoutingKey) {
		this.rabbitTemplate = Objects.requireNonNull(rabbitTemplate, "rabbitTemplate must not be null");
		this.defaultExchange = defaultExchange;
		this.defaultRoutingKey = defaultRoutingKey;
	}

	/**
	 * 按默认交换机与默认路由键发送消息.
	 * @param payload 消息体,交给 {@link RabbitTemplate} 配置的消息转换器序列化
	 * @throws IllegalStateException 未配置默认路由键时抛出
	 */
	public void send(Object payload) {
		send(this.defaultExchange, requireRoutingKey(null), payload);
	}

	/**
	 * 按默认交换机与指定路由键发送消息.
	 * @param routingKey 路由键,可为空;为空时回退到默认路由键
	 * @param payload 消息体
	 * @throws IllegalStateException 入参与默认路由键均为空时抛出
	 */
	public void send(@Nullable String routingKey, Object payload) {
		send(this.defaultExchange, requireRoutingKey(routingKey), payload);
	}

	/**
	 * 按指定交换机与路由键发送消息.
	 * @param exchange 交换机,可为空;为空时回退到默认交换机
	 * @param routingKey 路由键,可为空;为空时回退到默认路由键
	 * @param payload 消息体
	 * @throws IllegalStateException 入参与默认路由键均为空时抛出
	 */
	public void send(@Nullable String exchange, @Nullable String routingKey, Object payload) {
		this.rabbitTemplate.convertAndSend(resolveExchange(exchange), requireRoutingKey(routingKey), payload);
	}

	/**
	 * 按默认交换机与指定路由键发送并等待回复.
	 * @param routingKey 路由键,可为空;为空时回退到默认路由键
	 * @param payload 消息体
	 * @return 对端返回的结果,可能为 {@code null}
	 * @throws IllegalStateException 入参与默认路由键均为空时抛出
	 */
	public @Nullable Object sendAndReceive(@Nullable String routingKey, Object payload) {
		return sendAndReceive(this.defaultExchange, routingKey, payload);
	}

	/**
	 * 按指定交换机与路由键发送并等待回复.
	 * <p>
	 * 需要底层 {@link RabbitTemplate} 已配置回复队列或启用 direct reply-to;否则由 Spring AMQP 抛出异常。
	 * @param exchange 交换机,可为空;为空时回退到默认交换机
	 * @param routingKey 路由键,可为空;为空时回退到默认路由键
	 * @param payload 消息体
	 * @return 对端返回的结果,可能为 {@code null}
	 * @throws IllegalStateException 入参与默认路由键均为空时抛出
	 */
	public @Nullable Object sendAndReceive(@Nullable String exchange, @Nullable String routingKey, Object payload) {
		return this.rabbitTemplate.convertSendAndReceive(resolveExchange(exchange), requireRoutingKey(routingKey),
				payload);
	}

	private String resolveExchange(@Nullable String exchange) {
		if (StringUtils.hasText(exchange)) {
			return exchange;
		}
		return StringUtils.hasText(this.defaultExchange) ? this.defaultExchange : DEFAULT_EXCHANGE;
	}

	private String requireRoutingKey(@Nullable String routingKey) {
		if (StringUtils.hasText(routingKey)) {
			return routingKey;
		}
		if (StringUtils.hasText(this.defaultRoutingKey)) {
			return this.defaultRoutingKey;
		}
		throw new IllegalStateException(
				"routingKey must not be empty; configure butterfly.rabbitmq.default-routing-key");
	}

}
