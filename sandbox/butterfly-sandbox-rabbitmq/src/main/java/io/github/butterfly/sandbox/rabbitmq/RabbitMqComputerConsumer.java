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

package io.github.butterfly.sandbox.rabbitmq;

import com.rabbitmq.client.Channel;
import io.github.butterfly.sandbox.model.Computer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 消费端只需在 {@code @RabbitListener} 上指定 {@code containerFactory}:拓扑、JSON 转换与「重试 → 死信」链路全部由
 * {@code @EnableRabbitMqTemplates} 与 {@code butterfly.rabbitmq.*} 提供.
 * <p>
 * 确认模式默认是 {@code MANUAL}(见
 * {@code butterfly.rabbitmq.listener.acknowledge-mode}),所以这里必须自己确认, 用的是 Spring AMQP
 * 的手动确认写法:注入 {@link Channel} 加 {@code AmqpHeaders.DELIVERY_TAG}。
 */
@Slf4j
@Component
public class RabbitMqComputerConsumer {

	/**
	 * 必须与 {@code application.yml} 中 {@code butterfly.rabbitmq.entities.computer.queue}
	 * 一致.
	 */
	public static final String QUEUE = "butterfly-sandbox-computer-queue";

	/**
	 * 收到的消息,供测试断言(投递与断言不在同一线程).
	 */
	public static final BlockingQueue<Computer> RECEIVED = new LinkedBlockingQueue<>();

	/**
	 * 接收消息:本方法抛异常时,容器内的重试通知链按配置退避重试,耗尽后由 {@code RepublishMessageRecoverer} 投递到
	 * {@code butterfly-sandbox-computer.dlx}.
	 * @param computer JSON 转换后的消息体
	 * @param channel 当前通道,手动确认用
	 * @param deliveryTag 本次投递的标签
	 * @throws IOException 确认失败时抛出
	 */
	@RabbitListener(queues = QUEUE, containerFactory = "computerRabbitListenerContainerFactory")
	void onComputer(Computer computer, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
			throws IOException {
		log.info("Received computer: {}", computer);
		RECEIVED.add(computer);
		channel.basicAck(deliveryTag, false);
	}

}
