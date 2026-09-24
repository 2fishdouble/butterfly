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
 * Kafka 消费端示例包.
 * <p>
 * 演示配置驱动的重试与死信:监听方法<b>不</b>标注 {@code @RetryableTopic},只要落在实体主题上, 就会自动套用
 * {@code butterfly.kafka.topic.*} 的退避与死信策略,死信处理方法也由该配置指定.
 */
@NullMarked
package io.github.butterfly.sandbox.kafka;

import org.jspecify.annotations.NullMarked;
