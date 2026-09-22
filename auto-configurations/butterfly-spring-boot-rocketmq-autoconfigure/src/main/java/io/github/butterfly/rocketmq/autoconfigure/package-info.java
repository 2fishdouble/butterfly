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
 * RocketMQ 自动配置:在类路径存在 rocketmq-v5-client-spring-boot-starter 时,按实体类声明主题、消费组、
 * 死信主题与绑定好主题的发送器.
 * <p>
 * 本包不注册 {@code RocketMQClientTemplate} 与生产者/消费者 Builder:它们由 starter 自身的自动配置完成,这里只复用
 * 那一个模板,并为每个实体类补上主题名与死信主题名。
 */
@NullMarked
package io.github.butterfly.rocketmq.autoconfigure;

import org.jspecify.annotations.NullMarked;
