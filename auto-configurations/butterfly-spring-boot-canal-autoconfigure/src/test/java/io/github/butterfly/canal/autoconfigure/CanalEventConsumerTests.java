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

package io.github.butterfly.canal.autoconfigure;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 校验同步与异步消费端的确认、回滚与生命周期:成功才确认、失败必回滚,异步消费真的并发处理且整批处理完才确认。
 * <p>
 * 全程使用测试用的消息来源,不连接 canal server。
 */
class CanalEventConsumerTests {

	private final CanalProperties properties = new CanalProperties();

	@BeforeEach
	void setUp() {
		this.properties.setAutoStartup(false);
		this.properties.setErrorBackOff(Duration.ZERO);
		this.properties.setTimeout(Duration.ofMillis(10));
	}

	@Test
	void syncConsumerAcknowledgesAfterHandlingEveryEvent() throws Exception {
		TestCanalMessageSource source = new TestCanalMessageSource();
		CountDownLatch handled = new CountDownLatch(2);
		CanalEventDispatcher dispatcher = dispatcherFor((event) -> handled.countDown());
		source.addBatch(List.of(event(1L), event(2L)));

		SyncCanalEventConsumer consumer = new SyncCanalEventConsumer(source, dispatcher, this.properties);
		consumer.start();
		try {
			assertThat(consumer.isRunning()).isTrue();
			assertThat(handled.await(5, TimeUnit.SECONDS)).isTrue();

			awaitCall(source, "ack");
			assertThat(source.calls()).contains("connect").contains("ack").doesNotContain("rollback");
		}
		finally {
			consumer.stop();
		}

		assertThat(consumer.isRunning()).isFalse();
		assertThat(source.calls()).contains("close");
	}

	@Test
	void syncConsumerRollsBackWhenAHandlerFails() throws Exception {
		TestCanalMessageSource source = new TestCanalMessageSource();
		CanalEventDispatcher dispatcher = dispatcherFor((event) -> {
			throw new IllegalStateException("handler failed");
		});
		source.addBatch(List.of(event(1L)));

		SyncCanalEventConsumer consumer = new SyncCanalEventConsumer(source, dispatcher, this.properties);
		consumer.start();
		try {
			awaitCall(source, "rollback");
			assertThat(source.calls()).contains("rollback").doesNotContain("ack");
		}
		finally {
			consumer.stop();
		}
	}

	@Test
	void asyncConsumerHandlesEventsConcurrentlyAndAcknowledgesOnce() throws Exception {
		TestCanalMessageSource source = new TestCanalMessageSource();
		CyclicBarrier barrier = new CyclicBarrier(3);
		CountDownLatch handled = new CountDownLatch(3);
		CanalEventDispatcher dispatcher = dispatcherFor((event) -> {
			awaitBarrier(barrier);
			handled.countDown();
		});
		source.addBatch(List.of(event(1L), event(2L), event(3L)));

		ThreadPoolTaskExecutor executor = executor();
		try {
			AsyncCanalEventConsumer consumer = new AsyncCanalEventConsumer(source, dispatcher, this.properties,
					executor);
			consumer.start();
			try {
				// 三个事件必须同时在跑,否则栅栏超时、处理器抛异常、整批回滚,下面的断言自然失败
				assertThat(handled.await(10, TimeUnit.SECONDS)).isTrue();

				awaitCall(source, "ack");
				assertThat(source.calls()).contains("ack").doesNotContain("rollback");
			}
			finally {
				consumer.stop();
			}
		}
		finally {
			executor.shutdown();
		}
	}

	@Test
	void asyncConsumerRollsBackWhenAnyEventFails() throws Exception {
		TestCanalMessageSource source = new TestCanalMessageSource();
		CanalEventDispatcher dispatcher = dispatcherFor((event) -> {
			if ("2".equals(event.row().get("id"))) {
				throw new IllegalStateException("handler failed");
			}
		});
		source.addBatch(List.of(event(1L), event(2L), event(3L)));

		ThreadPoolTaskExecutor executor = executor();
		try {
			AsyncCanalEventConsumer consumer = new AsyncCanalEventConsumer(source, dispatcher, this.properties,
					executor);
			consumer.start();
			try {
				awaitCall(source, "rollback");
				assertThat(source.calls()).contains("rollback").doesNotContain("ack");
			}
			finally {
				consumer.stop();
			}
		}
		finally {
			executor.shutdown();
		}
	}

	@Test
	void startAndStopAreIdempotent() throws Exception {
		TestCanalMessageSource source = new TestCanalMessageSource();
		SyncCanalEventConsumer consumer = new SyncCanalEventConsumer(source, dispatcherFor((event) -> {
		}), this.properties);

		consumer.stop();
		consumer.start();
		consumer.start();
		assertThat(consumer.isRunning()).isTrue();
		awaitCall(source, "connect");

		consumer.stop();
		consumer.stop();
		assertThat(consumer.isRunning()).isFalse();
		assertThat(source.calls()).containsOnlyOnce("connect");
	}

	private static CanalEventDispatcher dispatcherFor(Consumer<CanalEvent> action) {
		CanalEventDispatcher dispatcher = new CanalEventDispatcher();
		for (CanalEventType eventType : CanalEventType.values()) {
			dispatcher.register(new CanalEventHandler() {

				@Override
				public @Nullable String schema() {
					return null;
				}

				@Override
				public String table() {
					return "computer";
				}

				@Override
				public CanalEventType eventType() {
					return eventType;
				}

				@Override
				public void handle(CanalEvent event) {
					action.accept(event);
				}

			});
		}
		return dispatcher;
	}

	private static CanalEvent event(long id) {
		return new CanalEvent("example", "butterfly", "computer", CanalEventType.INSERT,
				Map.of("id", String.valueOf(id)), null, null, Instant.EPOCH);
	}

	private static void awaitBarrier(CyclicBarrier barrier) {
		try {
			barrier.await(5, TimeUnit.SECONDS);
		}
		catch (Exception ex) {
			throw new IllegalStateException("handler did not run concurrently", ex);
		}
	}

	private static void awaitCall(TestCanalMessageSource source, String call) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (!source.calls().contains(call) && System.nanoTime() < deadline) {
			Thread.sleep(5);
		}
		assertThat(source.calls()).contains(call);
	}

	private static ThreadPoolTaskExecutor executor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(4);
		executor.setMaxPoolSize(4);
		executor.setThreadNamePrefix("canal-test-");
		executor.initialize();
		return executor;
	}

}
