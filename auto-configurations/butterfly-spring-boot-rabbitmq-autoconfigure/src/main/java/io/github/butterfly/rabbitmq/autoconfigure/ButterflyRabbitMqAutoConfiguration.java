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

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.ContainerCustomizer;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.CorrelationDataPostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * RabbitMQ 自动配置:唯一一个 JSON {@link MessageConverter}、"发送 ID"处理器,以及默认监听容器的手动确认模式.
 * <p>
 * <b>序列化</b>:注册一个 {@link JacksonJsonMessageConverter}(基于 Jackson 3,替代 Spring AMQP 中已过时的
 * {@code Jackson2JsonMessageConverter})。Boot 的 {@code RabbitTemplateConfigurer} 与
 * {@code AbstractRabbitListenerContainerFactoryConfigurer} 都只在容器中<b>恰好存在一个</b>
 * {@link MessageConverter} 时采用它,因此这一个 Bean 同时决定 {@code RabbitTemplate} 与默认监听容器工厂的收发
 * 格式;{@link RabbitMqRegistrar} 注册的各实体类监听容器工厂也取这同一个实例。
 * <p>
 * <b>发送 ID</b>:注册一个 {@link CorrelationDataPostProcessor},让每条发出的消息都带上一个可追踪的 ID;Boot
 * 不会自动把该处理器装到模板上,因此再加一个 {@link RabbitTemplateCustomizer} 完成装配。
 * <p>
 * <b>确认模式</b>:注册一个 {@link ContainerCustomizer},让 Boot 默认监听容器工厂(即 {@code @RabbitListener}
 * 未指定 {@code containerFactory} 时使用的 {@code rabbitListenerContainerFactory})也默认手动确认,与
 * {@code butterfly.rabbitmq.listener.acknowledge-mode} 的默认值保持一致。
 * <p>
 * 本模块<b>不</b>注册 {@code RabbitTemplate}:容器中始终只有 Boot 自动配置的那一个模板,使用方按
 * {@code convertAndSend(exchange, routingKey, payload)} 发送,交换机与路由键由
 * {@link RabbitMqProperties#resolve(Class)} 给出。这与 Kafka、Redis 的"按泛型注册多个模板"不同,因为 RabbitMQ
 * 的模板不分泛型,按实体类复制只会得到配置完全相同的多份实例。
 * <p>
 * 反序列化默认信任所有包({@code *}),与 Kafka 模块一致:消息头里的类型标识不受限制。
 * <p>
 * 需要收紧或改用其它序列化格式时,自行注册一个 {@link MessageConverter} Bean 即可,本配置随即退让。
 */
@AutoConfiguration(after = RabbitAutoConfiguration.class)
@ConditionalOnClass(RabbitTemplate.class)
public class ButterflyRabbitMqAutoConfiguration {

	/**
	 * 反序列化时信任的包,{@code *} 表示不限制.
	 */
	private static final String DEFAULT_TRUSTED_PACKAGES = "*";

	/**
	 * 注册容器中唯一的 JSON 消息转换器,让模板与各监听容器工厂统一按 JSON 收发.
	 * <p>
	 * 使用方自己定义了任意 {@link MessageConverter} Bean 时退让;此时模板与监听容器都改用使用方的转换器。
	 * @return JSON 消息转换器
	 */
	@Bean
	@ConditionalOnMissingBean(MessageConverter.class)
	MessageConverter butterflyRabbitJsonMessageConverter() {
		return new JacksonJsonMessageConverter(DEFAULT_TRUSTED_PACKAGES);
	}

	/**
	 * 注册"发送 ID"处理器:发送前拿到 {@link CorrelationData},把它的 id 写进消息属性 {@code correlationId},这样
	 * Broker 与消费端都能看到这个 ID.
	 * <p>
	 * 使用方传了 {@link CorrelationData} 就用它(于是 {@code getFuture()} 的 ack/nack 仍然对得上),没传就现造一个
	 * id 为随机 UUID 的实例。返回值必须是最终用于关联 confirm 的那个对象,因此原样返回入参或新造的对象。
	 * <p>
	 * <b>注意</b>:该处理器由 Spring AMQP 在 {@code RabbitTemplate.setupConfirm} 里调用,而这一步只在通道是
	 * {@code PublisherCallbackChannel} 时才会走到,即必须配置
	 * {@code spring.rabbitmq.publisher-confirm-type=correlated};否则它一次也不会被调用,消息上不会有 ID。
	 * <p>
	 * 使用方自定义 {@link CorrelationDataPostProcessor} Bean 时退让,但仍会被装到模板上。
	 * @return 相关数据处理器
	 */
	@Bean
	@ConditionalOnMissingBean(CorrelationDataPostProcessor.class)
	CorrelationDataPostProcessor butterflyRabbitCorrelationIdPostProcessor() {
		return (message, correlationData) -> {
			CorrelationData correlation = (correlationData != null) ? correlationData : new CorrelationData();
			String correlationId = correlation.getId();
			if (StringUtils.hasText(correlationId)) {
				message.getMessageProperties().setCorrelationId(correlationId);
			}
			return correlation;
		};
	}

	/**
	 * 把 {@link CorrelationDataPostProcessor} 装到 Boot 自动配置的 {@code RabbitTemplate} 上.
	 * <p>
	 * Boot 只提供了 setter,不会自己从容器里找这个类型的 Bean,所以必须在这里显式装配。
	 * <p>
	 * 使用方自己定义了 {@code RabbitTemplate} 时 Boot 的模板配置整体退让,本定制器也就不会被应用。
	 * @param processor 容器中的相关数据处理器
	 * @return 模板定制器
	 */
	@Bean
	RabbitTemplateCustomizer butterflyRabbitCorrelationIdTemplateCustomizer(CorrelationDataPostProcessor processor) {
		return (template) -> template.setCorrelationDataPostProcessor(processor);
	}

	/**
	 * 把 Boot 默认监听容器工厂创建的容器设为 {@link AcknowledgeMode#MANUAL}.
	 * <p>
	 * Boot 的默认工厂只通过 {@code ObjectProvider#ifUnique} 应用<b>唯一一个</b>
	 * {@link ContainerCustomizer},因此这里用 {@code @ConditionalOnMissingBean} 让位:使用方自己定义了
	 * {@link ContainerCustomizer} 时本配置退让,那个 Bean 才会被 Boot 采用。
	 * <p>
	 * {@code spring.rabbitmq.listener.simple.acknowledge-mode} 显式配置过时不覆盖,与 Boot 的一贯约定一致。本
	 * 定制器只管 Boot 的默认工厂;{@link RabbitMqRegistrar} 注册的各实体类监听容器工厂由
	 * {@code butterfly.rabbitmq.listener.acknowledge-mode} 独立控制。
	 * <p>
	 * 方法名带 {@code Rabbit} 前缀是必须的:Kafka 模块也有一个同名的确认模式定制器,两个模块同时出现在类路径上时 Bean 名会撞车,而 Boot
	 * 默认禁止 Bean 定义覆盖,应用会直接启动失败。
	 * @param properties 容器中的 Rabbit 配置,用于判断使用方是否显式配置了确认模式
	 * @return 默认监听容器定制器
	 */
	@Bean
	@ConditionalOnMissingBean(ContainerCustomizer.class)
	ContainerCustomizer<SimpleMessageListenerContainer> butterflyRabbitManualAckContainerCustomizer(
			RabbitProperties properties) {
		return (container) -> {
			if (properties.getListener().getSimple().getAcknowledgeMode() == null) {
				container.setAcknowledgeMode(AcknowledgeMode.MANUAL);
			}
		};
	}

}
