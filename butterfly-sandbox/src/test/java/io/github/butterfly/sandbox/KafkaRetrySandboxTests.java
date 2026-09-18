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

import io.github.butterfly.sandbox.model.TimeModuleBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 校验「配置驱动」的重试与死信链路:消费端<b>没有</b>任何 {@code @RetryableTopic} 注解,失败消息仍然按
 * {@code butterfly.kafka.topic.retry.*} 的配置重试,重试耗尽后进入死信主题并由配置指定的处理器消费.
 * <p>
 * 这里刻意选择 {@code TimeModuleBean} —— 它在主程序里只有实体注册、没有监听器,因此本用例同时验证了 「只要实体被
 * {@code @EnableKafkaTemplates} 声明,重试/死信主题就默认建立」这条链路。
 */
@SpringBootTest(properties = { "spring.kafka.bootstrap-servers=" + KafkaRetrySandboxTests.BROKER,
		"butterfly.kafka.topic.retry.attempts=3", "butterfly.kafka.topic.retry.back-off.delay=200ms",
		"butterfly.kafka.topic.retry.back-off.multiplier=2.0", "butterfly.kafka.topic.retry.back-off.max-delay=3s",
		"butterfly.kafka.topic.entities.timeModuleBean.retry.dlt-handler=timeModuleDltRecorder#onDlt" })
class KafkaRetrySandboxTests {

	/**
	 * 测试自带的 Kafka Broker 地址,不依赖 {@code application.yml}.
	 */
	static final String BROKER = "192.168.12.29:9092";

	private static final String TOPIC = "timeModuleBean";

	@Autowired
	private KafkaTemplate<String, TimeModuleBean> timeModuleBeanKafkaTemplate;

	@BeforeEach
	void resetRecorders() {
		FailingTimeModuleConsumer.ATTEMPTS.set(0);
		TimeModuleDltRecorder.RECEIVED.clear();
	}

	/**
	 * 一条必然失败的消息应当被投递 {@code attempts} 次,然后落到死信主题.
	 * <p>
	 * 死信主题与主主题一样跨运行保留消息,而测试消费组首次启动会从最早位移读取,因此不能只看"死信队列里有没有 消息":必须先等重试次数达标,再只认本次发送的那一条。
	 */
	@Test
	void failingMessageIsRetriedThenHandledByConfiguredDltHandler() throws Exception {
		TimeModuleBean bean = new TimeModuleBean();

		this.timeModuleBeanKafkaTemplate.send(TOPIC, "time-module-1", bean).get(15, TimeUnit.SECONDS);

		assertThat(awaitAttempts(3, 30)).as("attempts=3 应当投递 3 次(首次 + 2 次重试)").isTrue();

		TimeModuleBean dead = awaitDlt(bean, 30);
		assertThat(dead).as("消息应当在重试耗尽后进入死信主题").isNotNull();
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

	private TimeModuleBean awaitDlt(TimeModuleBean sent, long timeoutSeconds) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
		while (System.nanoTime() < deadline) {
			TimeModuleBean candidate = TimeModuleDltRecorder.RECEIVED.poll(1, TimeUnit.SECONDS);
			if (candidate != null && sent.getNow().equals(candidate.getNow())) {
				return candidate;
			}
		}
		return null;
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
	 * 必然失败的监听器:记录被投递的次数后抛异常.
	 */
	static class FailingTimeModuleConsumer {

		static final AtomicInteger ATTEMPTS = new AtomicInteger();

		@KafkaListener(topics = TOPIC, groupId = "butterfly-sandbox-retry-test")
		void onTimeModule(TimeModuleBean bean, Acknowledgment acknowledgment) {
			ATTEMPTS.incrementAndGet();
			throw new IllegalStateException("always fails");
		}

	}

	/**
	 * 由 {@code ...retry.dlt-handler=timeModuleDltRecorder#onDlt} 指定的死信处理器.
	 */
	static class TimeModuleDltRecorder {

		static final BlockingQueue<TimeModuleBean> RECEIVED = new LinkedBlockingQueue<>();

		void onDlt(TimeModuleBean bean) {
			RECEIVED.add(bean);
		}

	}

}
