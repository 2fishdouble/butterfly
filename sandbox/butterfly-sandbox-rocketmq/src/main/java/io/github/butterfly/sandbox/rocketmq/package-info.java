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
 * RocketMQ 消费端示例包.
 * <p>
 * 提供四个顺序消费的消费者:两个按 {@code tag1} 标签过滤且分属不同消费组,一个不过滤标签, 另一个订阅 {@code exception}
 * 标签并主动抛出业务异常,用于演示消费失败后的重试.
 */
@NullMarked
package io.github.butterfly.sandbox.rocketmq;

import org.jspecify.annotations.NullMarked;
