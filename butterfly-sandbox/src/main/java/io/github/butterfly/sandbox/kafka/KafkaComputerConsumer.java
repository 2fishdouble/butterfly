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

@Slf4j
// @Component
public class KafkaComputerConsumer {

	private static final String TOPIC = "butterfly-sandbox-computer";

	static final String GROUP_ID = "butterfly-sandbox-test";

	/**
	 * Receive computer. CONSUMER LAG = 0.表示当前消费确认完了的.
	 * @param computer computer
	 * @param acknowledgment acknowledgment
	 */
	@KafkaListener(topics = TOPIC, groupId = GROUP_ID)
	void onComputer(Computer computer, Acknowledgment acknowledgment) {
		log.info("Received computer: {}", computer);
		acknowledgment.acknowledge();
	}

}
