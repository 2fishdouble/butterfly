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

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 注册基于 {@link RabbitTemplate} 的 {@link RabbitMqTemplate} 门面.
 * <p>
 * 须在 Boot 的 {@link RabbitAutoConfiguration} 之后执行; 只有存在可用的 {@link RabbitTemplate} bean
 * 时才注册,否则优雅跳过。
 */
@AutoConfiguration(after = RabbitAutoConfiguration.class)
@EnableConfigurationProperties(RabbitMqTemplateProperties.class)
@ConditionalOnClass({ RabbitAutoConfiguration.class, RabbitTemplate.class })
public class ButterflyRabbitMqAutoConfiguration {

	/**
	 * 注册 {@link RabbitMqTemplate} bean.
	 * <p>
	 * 仅当容器中已存在 {@link RabbitTemplate} bean、且使用方未自行定义 {@link RabbitMqTemplate} 时才创建;
	 * 默认交换机与默认路由键取自 {@link RabbitMqTemplateProperties} 配置。
	 * @param rabbitTemplate 由 Boot AMQP 自动配置提供的 Rabbit 模板
	 * @param properties 用于解析默认交换机与默认路由键的配置
	 * @return 使用给定模板与默认交换机/路由键构造的门面实例
	 */
	@Bean
	@ConditionalOnBean(RabbitTemplate.class)
	@ConditionalOnMissingBean(RabbitMqTemplate.class)
	public RabbitMqTemplate rabbitMqTemplate(RabbitTemplate rabbitTemplate, RabbitMqTemplateProperties properties) {
		return new RabbitMqTemplate(rabbitTemplate, properties.getDefaultExchange(), properties.getDefaultRoutingKey());
	}

}
