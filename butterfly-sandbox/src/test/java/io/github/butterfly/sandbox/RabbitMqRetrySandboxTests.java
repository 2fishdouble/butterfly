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

package io.github.butterfly.sandbox;

import com.rabbitmq.client.Channel;
import io.github.butterfly.rabbitmq.autoconfigure.RabbitMqProperties;
import io.github.butterfly.sandbox.model.TimeModuleBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.handler.annotation.Header;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 校验「配置驱动」的重试与死信链路:消费端<b>没有</b>任何额外注解,失败消息仍然按 {@code butterfly.rabbitmq.retry.*}
 * 在容器内退避重试;重试耗尽后拒绝原投递,Broker 再按主队列上的 {@code x-dead-letter-*} 把它 dead-letter 到该实体类的死信队列.
 * <p>
 * 这里刻意选择 {@code TimeModuleBean} —— 它在主程序里只有实体注册、没有监听器,因此本用例同时验证了「只要实体被
 * {@code @EnableRabbitMqTemplates} 声明,死信拓扑就默认建立」这条链路。
 * <p>
 * 确认模式<b>不</b>做任何覆盖,用默认的 {@code MANUAL}:这正是最容易出问题的组合 —— 如果恢复器只是自己 republish 一份
 * 而不拒绝原投递,原消息会一直停在 unacked,通道关闭后被重投,死信队列收到副本、主队列又重来一遍。本用例断言死信队列里 那条消息带着 Broker 写的
 * {@code x-death(reason=rejected)},以此证明原投递确实被拒绝结算了。
 */
@SpringBootTest(properties = { "butterfly.rabbitmq.retry.attempts=3", "butterfly.rabbitmq.retry.delay=100ms",
		"butterfly.rabbitmq.retry.multiplier=1.0", "butterfly.rabbitmq.retry.max-delay=500ms" })
class RabbitMqRetrySandboxTests {

	private static final String QUEUE = "timeModuleBean";

	private static final String DEAD_LETTER_QUEUE = "timeModuleBean.dlq";

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private AmqpAdmin amqpAdmin;

	@Autowired
	private RabbitMqProperties rabbitMqProperties;

	@BeforeEach
	void reset() {
		FailingTimeModuleConsumer.ATTEMPTS.set(0);
		TimeModuleDltRecorder.RECEIVED.clear();
		// 队列跨运行保留消息,先清干净,免得上一轮的残留影响本次的投递次数
		this.amqpAdmin.purgeQueue(QUEUE);
		this.amqpAdmin.purgeQueue(DEAD_LETTER_QUEUE);
	}

	/**
	 * 死信名称按默认规则从主名称派生,顺便把 yml/测试常量与解析结果绑死.
	 */
	@Test
	void deadLetterTopologyFollowsDefaultSuffixes() {
		RabbitMqProperties.EntityDefinition definition = this.rabbitMqProperties.resolve(TimeModuleBean.class);

		assertThat(definition.queue()).isEqualTo(QUEUE);
		assertThat(definition.deadLetterExchange()).isEqualTo("timeModuleBean.dlx");
		assertThat(definition.deadLetterQueue()).isEqualTo(DEAD_LETTER_QUEUE);
		assertThat(definition.deadLetterRoutingKey()).isEqualTo("timeModuleBean.dead");
		assertThat(definition.attempts()).isEqualTo(3);
		assertThat(this.amqpAdmin.getQueueProperties(DEAD_LETTER_QUEUE)).isNotNull();
	}

	/**
	 * 一条必然失败的消息应当被投递 {@code attempts} 次,然后被拒绝并 dead-letter 到死信队列。
	 * <p>
	 * 死信队列里那条消息必须带 Broker 写的 {@code x-death(reason=rejected)}:Broker 只在消息被 reject/nack 或被
	 * TTL 过期时才写这个头,因此它同时证明了两件事 —— 消息确实进了死信队列,以及原投递是被<b>拒绝结算</b>的(而不是被谁偷偷 republish
	 * 一份、把原投递丢在 unacked)。
	 */
	@Test
	void failingMessageIsRetriedThenDeadLetteredByBroker() throws Exception {
		RabbitMqProperties.EntityDefinition definition = this.rabbitMqProperties.resolve(TimeModuleBean.class);
		TimeModuleBean bean = new TimeModuleBean();

		this.rabbitTemplate.convertAndSend(definition.exchange(), definition.routingKey(), bean);

		assertThat(awaitAttempts(3, 30)).as("attempts=3 应当投递 3 次(首次 + 2 次重试)").isTrue();

		DeadLetter dead = awaitDeadLetter(bean, 30);
		assertThat(dead).as("重试耗尽后应当被 dead-letter 到 " + DEAD_LETTER_QUEUE).isNotNull();
		assertThat(dead.bean().getNow()).isEqualTo(bean.getNow());

		assertThat(dead.xDeath()).as("死信消息必须带 Broker 写的 x-death").isNotEmpty();
		assertThat(dead.xDeath().getFirst().get("reason")).isEqualTo("rejected");
		assertThat(dead.xDeath().getFirst().get("queue")).isEqualTo(QUEUE);
	}

	private boolean awaitAttempts(int expected, long timeoutSeconds) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
		while (System.nanoTime() < deadline) {
			if (FailingTimeModuleConsumer.ATTEMPTS.get() >= expected) {
				return true;
			}
			Thread.sleep(50);
		}
		return false;
	}

	/**
	 * 死信队列与主队列一样跨运行保留消息,因此不能只看"队列里有没有消息":只认本次发送的那一条。
	 */
	private DeadLetter awaitDeadLetter(TimeModuleBean sent, long timeoutSeconds) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
		while (System.nanoTime() < deadline) {
			DeadLetter candidate = TimeModuleDltRecorder.RECEIVED.poll(1, TimeUnit.SECONDS);
			if (candidate != null && sent.getNow().equals(candidate.bean().getNow())) {
				return candidate;
			}
		}
		throw new AssertionError("等待死信超时: " + DEAD_LETTER_QUEUE);
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class RetryConfiguration {

		@Bean
		FailingTimeModuleConsumer failingTimeModuleConsumer() {
			return new FailingTimeModuleConsumer();
		}

		@Bean
		TimeModuleDltRecorder timeModuleDltRecorder() {
			return new TimeModuleDltRecorder();
		}

	}

	/**
	 * 必然失败的监听器:记录被投递的次数后抛异常,既不 ack 也不 nack,交给容器内的重试通知链处理.
	 */
	static class FailingTimeModuleConsumer {

		static final AtomicInteger ATTEMPTS = new AtomicInteger();

		@RabbitListener(queues = QUEUE, containerFactory = "timeModuleBeanRabbitListenerContainerFactory")
		void onTimeModule(TimeModuleBean bean, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
				throws IOException {
			ATTEMPTS.incrementAndGet();
			throw new IllegalStateException("always fails");
		}

	}

	/**
	 * 死信队列的消费者:把消息体和 Broker 写的 {@code x-death} 一起记下来,供断言判断它是被拒绝进来的.
	 */
	static class TimeModuleDltRecorder {

		static final BlockingQueue<DeadLetter> RECEIVED = new LinkedBlockingQueue<>();

		@RabbitListener(queues = DEAD_LETTER_QUEUE)
		void onDeadLetter(TimeModuleBean bean,
				@Header(name = "x-death", required = false) List<Map<String, Object>> xDeath, Channel channel,
				@Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
			RECEIVED.add(new DeadLetter(bean, xDeath));
			channel.basicAck(deliveryTag, false);
		}

	}

	/**
	 * 死信队列上收到的一条消息.
	 *
	 * @param bean 消息体
	 * @param xDeath Broker 写的 {@code x-death} 头
	 */
	record DeadLetter(TimeModuleBean bean, List<Map<String, Object>> xDeath) {
	}

}
