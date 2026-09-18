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
 * RabbitMQ 自动配置:在类路径存在 Spring AMQP 时注册容器中唯一的 JSON 消息转换器,并按实体类声明式地创建交换机、
 * 队列、绑定、死信拓扑、延时拓扑与带重试的监听容器工厂.
 * <p>
 * 本包不注册 {@code RabbitTemplate}:收发统一复用 Boot 自动配置的那一个模板,发送时显式给出交换机与路由键。
 */
@NullMarked
package io.github.butterfly.rabbitmq.autoconfigure;

import org.jspecify.annotations.NullMarked;
