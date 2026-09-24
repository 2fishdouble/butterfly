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

package io.github.butterfly.sandbox.rocketmq;

import io.github.butterfly.core.BusinessException;
import io.github.butterfly.sandbox.model.Computer;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Component
@Slf4j
public class RocketmqComputerConsumer {

	/**
	 * Topic.
	 */
	public static final String TOPIC = "butterfly-sandbox-computer";

	/**
	 * Tag.
	 */
	public static final String TAG = "tag1";

	/**
	 * Exception tag.
	 */
	public static final String EXCEPTION_TAG = "exception";

	/**
	 * Group1.
	 */

	public static final String GROUP1 = "group1";

	/**
	 * Group2.
	 */
	public static final String GROUP2 = "group2";

	@Service
	@RocketMQMessageListener(topic = RocketmqComputerConsumer.TOPIC, selectorExpression = RocketmqComputerConsumer.TAG,
			consumerGroup = RocketmqComputerConsumer.GROUP1, consumeMode = ConsumeMode.ORDERLY)
	public static class Consumer1 implements RocketMQListener<Computer> {

		@Override
		public void onMessage(Computer computer) {
			log.info("Consumer1 监听到消息：{}", computer);
		}

	}

	@Service
	@RocketMQMessageListener(topic = RocketmqComputerConsumer.TOPIC, selectorExpression = RocketmqComputerConsumer.TAG,
			consumerGroup = RocketmqComputerConsumer.GROUP2, consumeMode = ConsumeMode.ORDERLY)
	public static class Consumer2 implements RocketMQListener<Computer> {

		@Override
		public void onMessage(Computer computer) {
			log.info("Consumer2 监听到消息：{}", computer);
		}

	}

	@Service
	@RocketMQMessageListener(topic = RocketmqComputerConsumer.TOPIC, consumerGroup = RocketmqComputerConsumer.GROUP1,
			consumeMode = ConsumeMode.ORDERLY)
	public static class Consumer3 implements RocketMQListener<Computer> {

		@Override
		public void onMessage(Computer computer) {
			log.info("Consumer3 监听到消息：{}", computer);
		}

	}

	@Service
	@RocketMQMessageListener(topic = RocketmqComputerConsumer.TOPIC, consumerGroup = RocketmqComputerConsumer.GROUP1,
			selectorExpression = RocketmqComputerConsumer.EXCEPTION_TAG, consumeMode = ConsumeMode.ORDERLY,
			maxReconsumeTimes = 3)
	public static class Consumer4 implements RocketMQListener<Computer> {

		@Override
		public void onMessage(Computer computer) {
			log.info("Consumer4 监听到消息：{}", computer);
			throw new BusinessException("模拟业务异常");
		}

	}

}
