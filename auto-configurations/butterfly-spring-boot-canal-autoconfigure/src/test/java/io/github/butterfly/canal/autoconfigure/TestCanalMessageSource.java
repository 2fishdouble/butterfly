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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
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
	 * 已发起的拉取次数.
	 * @return 拉取次数
	 */
	int pollCount() {
		return this.polls.get();
	}

	@Override
	public void connect() {
		this.calls.add("connect");
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
		this.calls.add("ack");
	}

	@Override
	public void rollback() {
		this.calls.add("rollback");
	}

	@Override
	public void close() {
		this.calls.add("close");
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
