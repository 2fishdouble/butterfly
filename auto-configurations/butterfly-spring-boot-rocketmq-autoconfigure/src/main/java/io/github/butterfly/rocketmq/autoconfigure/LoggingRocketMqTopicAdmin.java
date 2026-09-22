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

/**
 * 兜底的 {@link RocketMqTopicAdmin}:只把"本该建哪个主题"记进日志,不做任何网络调用.
 * <p>
 * RocketMQ 5 的 gRPC 客户端没有任何建主题的 API,而 RocketMQ 5 的 Broker 默认会在生产者首次发送或消费者首次订阅时
 * 自动创建主题,因此多数部署下"什么都不做"就是正确行为。需要显式建主题时,自己注册一个 {@link RocketMqTopicAdmin} Bean 即可,本实现随即退让。
 */
public class LoggingRocketMqTopicAdmin implements RocketMqTopicAdmin {

	private static final Log logger = LogFactory.getLog(LoggingRocketMqTopicAdmin.class);

	@Override
	public void createTopic(String topic, int queueCount) {
		logger.warn("Skip creating RocketMQ topic '" + topic + "' with " + queueCount
				+ " queues: the RocketMQ 5 gRPC client exposes no topic administration API, so the broker has to "
				+ "create it on first use (autoCreateTopicEnable), or you define your own RocketMqTopicAdmin bean");
	}

}
