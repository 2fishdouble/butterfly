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

package io.github.butterfly.sandbox.kafka;

import io.github.butterfly.sandbox.model.Computer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * 消费端无需标注 {@code @RetryableTopic}:落在 {@code @EnableKafkaTemplates} 所声明实体主题上的监听方法, 会自动套用
 * {@code butterfly.kafka.topic.*} 里的重试与死信策略.
 * <p>
 * 死信处理方法通过
 * {@code butterfly.kafka.topic.entities.computer.retry.dlt-handler=kafkaComputerConsumer#onDlt}
 * 指定: 配置驱动的重试链路不会扫描 {@code @DltHandler} 注解(该注解只在 {@code @RetryableTopic} 下生效).
 */
@Slf4j
@Component
public class KafkaComputerConsumer {

	private static final String TOPIC = "butterfly-sandbox-computer";

	static final String GROUP_ID = "butterfly-sandbox-test";

	/**
	 * 接收消息;本方法抛出异常时,框架按配置的重试策略转发到重试主题,重试耗尽后进入死信主题.
	 * @param computer computer
	 * @param acknowledgment acknowledgment
	 */
	@KafkaListener(topics = TOPIC, groupId = GROUP_ID)
	void onComputer(Computer computer, Acknowledgment acknowledgment) {
		log.info("Received computer: {}", computer);
		acknowledgment.acknowledge();
	}

	/**
	 * 死信处理方法:重试次数耗尽后由框架回调.
	 * @param computer computer
	 */
	void onDlt(Computer computer) {
		log.error("Computer message sent to DLT: {}", computer);
	}

}
