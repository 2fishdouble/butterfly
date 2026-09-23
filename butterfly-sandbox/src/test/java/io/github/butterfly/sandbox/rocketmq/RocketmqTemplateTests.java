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

import cn.hutool.core.util.IdUtil;
import io.github.butterfly.sandbox.model.Computer;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.support.MessageBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@SpringBootTest
public class RocketmqTemplateTests {

	@Autowired
	private RocketMQTemplate rocketMQTemplate;

	@Test
	void syncSend() {
		this.rocketMQTemplate.syncSend(RocketmqComputerConsumer.TOPIC,
				MessageBuilder.withPayload(sampleComputer()).build());
		this.rocketMQTemplate.syncSend(RocketmqComputerConsumer.TOPIC + ":" + RocketmqComputerConsumer.TAG,
				MessageBuilder.withPayload(sampleComputer()).build());
		this.rocketMQTemplate.syncSend(RocketmqComputerConsumer.TOPIC + ":" + RocketmqComputerConsumer.EXCEPTION_TAG,
				MessageBuilder.withPayload(sampleComputer()).build());
	}

	private Computer sampleComputer() {
		Computer.Product product = new Computer.Product();
		product.setId(IdUtil.getSnowflakeNextId());
		product.setName("keyboard");
		product.setDescription("mechanical keyboard");
		product.setPrice(new BigDecimal("199.00"));
		product.setQuantity(2);
		product.setTotalAmount(new BigDecimal("398.00"));

		Computer computer = new Computer();
		computer.setId(IdUtil.getSnowflakeNextId());
		computer.setName("butterfly-sandbox");
		computer.setCreateTime(LocalDateTime.now());
		computer.setProducts(List.of(product));
		return computer;
	}

}
