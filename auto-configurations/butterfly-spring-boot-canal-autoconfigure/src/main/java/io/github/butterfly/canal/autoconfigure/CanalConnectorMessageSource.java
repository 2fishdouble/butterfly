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

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.impl.SimpleCanalConnector;
import com.alibaba.otter.canal.protocol.Message;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * canal 消息来源:包装 canal-client 的 {@link CanalConnector},直连单个 canal server.
 * <p>
 * 拉取走 {@code getWithoutAck}(不自动确认),处理成功后再按 batchId 调用 {@code ack}、失败调用
 * {@code rollback},因此应用崩溃时未确认的数据会在 canal server 侧保留并重新投递。
 * <p>
 * 一次 {@code getWithoutAck} 拿到的消息可能包含多行变更,它们属于同一个 batchId,会被拆成多个 {@link CanalEvent}
 * 作为一个批次交给消费端,整批一起确认或回滚。
 * <p>
 * <b>连接器是延迟创建的</b>:{@link #connect()} 才向 {@link Supplier} 要连接器,因此应用启动阶段不会创建 netty
 * 客户端,也不会因为 canal server 暂时不可用而失败。
 */
public class CanalConnectorMessageSource implements CanalMessageSource {

	private static final Logger log = LoggerFactory.getLogger(CanalConnectorMessageSource.class);

	/**
	 * 没有未确认批次时的 batchId,与 canal 侧表示"没有数据"的取值一致.
	 */
	private static final long NO_BATCH = -1L;

	private final Supplier<CanalConnector> connectorSupplier;

	private final String destination;

	private final int batchSize;

	private final @Nullable String filter;

	private @Nullable CanalConnector connector;

	private boolean connected;

	private long pendingBatchId = NO_BATCH;

	/**
	 * 创建消息来源.
	 * @param connectorSupplier 连接器供应者,在 {@link #connect()} 时调用,可以在这里做建连接以外的准备工作
	 * @param destination canal 实例名,写进每个事件
	 * @param batchSize 单次拉取的最大事件条数
	 * @param filter 订阅表达式;为空时使用 canal server 侧的配置
	 */
	public CanalConnectorMessageSource(Supplier<CanalConnector> connectorSupplier, String destination, int batchSize,
			@Nullable String filter) {
		this.connectorSupplier = connectorSupplier;
		this.destination = destination;
		this.batchSize = batchSize;
		this.filter = filter;
	}

	/**
	 * 创建连接器、建立连接并订阅.
	 * <p>
	 * 未配置订阅表达式时调用无参 {@code subscribe()},即完全使用 canal server 侧的 filter 配置。任何一步失败
	 * 都会把连接器置空,由消费端退避后重试整个流程。
	 */
	@Override
	public void connect() {
		CanalConnector target = this.connectorSupplier.get();
		target.connect();
		if (StringUtils.hasText(this.filter)) {
			target.subscribe(this.filter);
		}
		else {
			target.subscribe();
		}

		this.connector = target;
		this.connected = true;
	}

	/**
	 * 拉取一批事件.
	 * <p>
	 * 没有数据时返回空列表;拿到 batchId 但里面没有行变更(例如只有事务开始/结束或 DDL 条目)时立即确认,避免 这批数据一直挂在服务端。
	 * @param timeout 等待数据的超时时间
	 * @return 事件列表
	 * @throws IllegalStateException 尚未连接时抛出
	 */
	@Override
	public List<CanalEvent> poll(Duration timeout) {
		Message message = requireConnector().getWithoutAck(this.batchSize, timeout.toMillis(), TimeUnit.MILLISECONDS);
		if (message == null || message.getId() == NO_BATCH) {
			return List.of();
		}

		List<CanalEvent> events = CanalEventConverter.fromMessage(this.destination, message);
		if (events.isEmpty()) {
			requireConnector().ack(message.getId());
			return List.of();
		}

		this.pendingBatchId = message.getId();
		return events;
	}

	@Override
	public void ack() {
		long batchId = this.pendingBatchId;
		if (batchId != NO_BATCH) {
			requireConnector().ack(batchId);
			this.pendingBatchId = NO_BATCH;
		}
	}

	@Override
	public void rollback() {
		long batchId = this.pendingBatchId;
		if (batchId != NO_BATCH) {
			requireConnector().rollback(batchId);
			this.pendingBatchId = NO_BATCH;
		}
	}

	/**
	 * 打断阻塞中的拉取.
	 * <p>
	 * canal 的连接器提供了 {@code stopRunning},它会让 {@code getWithoutAck} 立刻返回或抛异常;停止后连接器不再可用,
	 * 正好符合本对象即将销毁的语义。连接器还没创建出来时什么都不用做。
	 */
	@Override
	public void wakeup() {
		CanalConnector target = this.connector;
		if (target instanceof SimpleCanalConnector simpleConnector) {
			simpleConnector.stopRunning();
		}
	}

	@Override
	public void close() {
		CanalConnector target = this.connector;
		this.connector = null;
		this.connected = false;
		this.pendingBatchId = NO_BATCH;
		if (target == null) {
			return;
		}

		try {
			target.disconnect();
		}
		catch (RuntimeException ex) {
			log.warn("Failed to disconnect canal connector: {}", ex.getMessage());
		}
	}

	/**
	 * 当前的 canal 连接器.
	 * @return {@link #connect()} 之后的连接器;尚未连接时为 {@code null}
	 */
	public @Nullable CanalConnector connector() {
		return this.connector;
	}

	private CanalConnector requireConnector() {
		CanalConnector target = this.connector;
		if (target == null || !this.connected) {
			throw new IllegalStateException("Canal connector is not connected; call connect() first");
		}
		return target;
	}

}
