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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 启动时按实体类建立 RocketMQ 主题.
 * <p>
 * 对所有 {@link RocketMqProperties.TopicDefinition} Bean 做一次遍历:开启了建主题开关的,调用
 * {@link RocketMqTopicAdmin#createTopic(String, int)};同一主题名被多个实体类命中时只建一次。
 * <p>
 * <b>只建主主题</b>。死信主题 {@code %DLQ%<消费组>} 与重试主题 {@code %RETRY%<消费组>} 是 RocketMQ 的系统主题,
 * Broker 会自行创建,显式创建会被 Broker 以"与系统主题冲突"拒绝,因此这里只把它们记进日志,方便运维在控制台上按名字找到 死信队列。
 */
public class RocketMqTopicInitializer implements SmartInitializingSingleton {

	private static final Log logger = LogFactory.getLog(RocketMqTopicInitializer.class);

	private final RocketMqTopicAdmin topicAdmin;

	private final List<RocketMqProperties.TopicDefinition> definitions;

	/**
	 * 创建一个初始化器.
	 * @param topicAdmin 主题管理器
	 * @param definitions 由 {@link RocketMqRegistrar} 按实体类注册的主题定义
	 */
	public RocketMqTopicInitializer(RocketMqTopicAdmin topicAdmin,
			List<RocketMqProperties.TopicDefinition> definitions) {
		this.topicAdmin = topicAdmin;
		this.definitions = List.copyOf(definitions);
	}

	@Override
	public void afterSingletonsInstantiated() {
		Set<String> created = new LinkedHashSet<>();
		for (RocketMqProperties.TopicDefinition definition : this.definitions) {
			logger.info("RocketMQ entity topic '" + definition.topic() + "' uses consumer group '"
					+ definition.consumerGroup() + "'; its dead letter topic is '" + definition.deadLetterTopic()
					+ "' (created by the broker, not by this initializer)");
			if (definition.createTopic() && created.add(definition.topic())) {
				this.topicAdmin.createTopic(definition.topic(), definition.queueCount());
			}
		}
	}

}
