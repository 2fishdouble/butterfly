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

import lombok.Data;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RabbitMQ 模板配置,前缀 {@code butterfly.rabbitmq}.
 */
@Data
@ConfigurationProperties(prefix = "butterfly.rabbitmq")
public class RabbitMqTemplateProperties {

	/**
	 * 默认交换机名称.发送时未显式指定交换机则取它;为空时使用 RabbitMQ 内置的默认交换机(空字符串).
	 */
	private @Nullable String defaultExchange;

	/**
	 * 默认路由键.发送时未显式指定路由键则取它;为空时要求调用方显式传入路由键,否则抛出异常.
	 */
	private @Nullable String defaultRoutingKey;

}
