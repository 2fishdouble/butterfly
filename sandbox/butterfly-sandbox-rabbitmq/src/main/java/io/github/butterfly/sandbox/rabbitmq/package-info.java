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

/**
 * RabbitMQ 消费端示例包.
 * <p>
 * 演示在 {@code @RabbitListener} 上只指定容器工厂的写法:拓扑、JSON 转换与「重试 → 死信」链路全部由
 * {@code @EnableRabbitMqTemplates} 与 {@code butterfly.rabbitmq.*} 提供;确认模式默认是
 * {@code MANUAL}, 因此消费端自己注入 {@code Channel} 并按投递标签手动确认.
 */
@NullMarked
package io.github.butterfly.sandbox.rabbitmq;

import org.jspecify.annotations.NullMarked;
