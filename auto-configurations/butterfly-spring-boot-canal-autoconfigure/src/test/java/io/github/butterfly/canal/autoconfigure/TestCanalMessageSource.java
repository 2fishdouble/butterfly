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

import java.time.Duration;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 测试用的消息来源:按批次回放预先放好的事件,并记录 {@code connect}/{@code ack}/{@code rollback}/{@code close}
 * 的调用顺序,替代需要真实 canal server 的连接器.
 * <p>
 * 没有数据时按传入的超时睡眠,模拟真实拉取的阻塞,避免消费循环空转吃满 CPU。
 */
class TestCanalMessageSource implements CanalMessageSource {

	private final Queue<List<CanalEvent>> batches = new ConcurrentLinkedQueue<>();

	private final List<String> calls = new CopyOnWriteArrayList<>();

	/**
	 * 与 {@link #calls} 同步的调用信号:测试线程阻塞等待它,而不是反复查询 {@link #calls}.
	 */
	private final BlockingQueue<String> signals = new LinkedBlockingQueue<>();

	private final AtomicInteger polls = new AtomicInteger();

	/**
	 * 排队一批事件,下一次 {@link #poll(Duration)} 会返回它们.
	 * @param events 本批事件
	 */
	void addBatch(List<CanalEvent> events) {
		this.batches.add(events);
	}

	/**
	 * 调用记录,取值形如 {@code connect}、{@code ack}、{@code rollback}、{@code close}.
	 * @return 调用记录
	 */
	List<String> calls() {
		return this.calls;
	}

	/**
	 * 阻塞等待某次调用发生.
	 * <p>
	 * 等待期间线程挂起,不做轮询;已经发生过的调用会留在队列里,因此调用之后再等待也能立刻返回。
	 * @param call 期望的调用名,取值同 {@link #calls()}
	 * @param timeout 最长等待时间
	 * @return 等到该调用时返回 {@code true},超时返回 {@code false}
	 * @throws InterruptedException 等待期间线程被中断时抛出
	 */
	boolean awaitCall(String call, Duration timeout) throws InterruptedException {
		long deadline = System.nanoTime() + timeout.toNanos();
		while (true) {
			long remaining = deadline - System.nanoTime();
			String signalled = this.signals.poll(remaining, TimeUnit.NANOSECONDS);
			if (signalled == null) {
				return false;
			}
			if (call.equals(signalled)) {
				return true;
			}
		}
	}

	/**
	 * 已发起的拉取次数.
	 * @return 拉取次数
	 */
	int pollCount() {
		return this.polls.get();
	}

	@Override
	public void connect() {
		record("connect");
	}

	@Override
	public List<CanalEvent> poll(Duration timeout) {
		this.polls.incrementAndGet();
		List<CanalEvent> batch = this.batches.poll();
		if (batch != null) {
			return batch;
		}

		sleep(timeout);
		return List.of();
	}

	@Override
	public void ack() {
		record("ack");
	}

	@Override
	public void rollback() {
		record("rollback");
	}

	@Override
	public void close() {
		record("close");
	}

	/**
	 * 记录一次调用:既进 {@link #calls} 供断言,也进 {@link #signals} 唤醒等待中的测试线程.
	 * @param call 调用名
	 */
	private void record(String call) {
		this.calls.add(call);
		this.signals.add(call);
	}

	private static void sleep(Duration timeout) {
		try {
			Thread.sleep(timeout.toMillis());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

}
