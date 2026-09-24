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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 消费端骨架:管好后台线程、拉取循环与"处理成功才确认、失败就回滚"的语义,把"怎么处理这一批"留给子类.
 * <p>
 * 循环逻辑:
 * <ol>
 * <li>启动后先 {@link CanalMessageSource#connect()} 建立连接;连接失败会按
 * {@code butterfly.canal.error-back-off} 退避后重试,因此 canal server 比应用晚启动也能自动接上;</li>
 * <li>反复 {@link CanalMessageSource#poll(Duration)}:没有数据就立刻进入下一次拉取;</li>
 * <li>拿到数据后交给子类处理,处理成功才 {@link CanalMessageSource#ack()},失败则
 * {@link CanalMessageSource#rollback()} 让整批重新投递,并退避一段时间避免紧循环刷日志;</li>
 * <li>{@link #stop()} 先置停止标志、再 {@link CanalMessageSource#wakeup()} 打断阻塞中的拉取,最后等后台
 * 线程退出并释放连接。</li>
 * </ol>
 * <p>
 * 因此整体是至少一次(at-least-once)语义:处理器需要自己保证幂等,并且可能收到重复事件。
 * <p>
 * 停止时不会中断后台线程(只唤醒拉取),避免正在执行的处理器被 {@link InterruptedException} 打断;若处理器长时间
 * 不返回,{@link #stop()} 会等待到超时后记录告警并继续关闭流程。
 * <p>
 * 拉取与处理阶段的失败都会被记录并退避重试,消费线程本身不会因为业务异常而退出;连接、拉取、处理、确认各自失败时 都会走同一条退避路径。
 */
public abstract class AbstractCanalEventConsumer implements CanalEventConsumer, SmartLifecycle {

	private static final Logger log = LoggerFactory.getLogger(AbstractCanalEventConsumer.class);

	/**
	 * 等待后台线程退出的最长时间.
	 */
	private static final Duration STOP_TIMEOUT = Duration.ofSeconds(30);

	private final CanalMessageSource messageSource;

	private final CanalEventDispatcher dispatcher;

	private final CanalProperties properties;

	private final AtomicBoolean running = new AtomicBoolean();

	private volatile boolean connected;

	private volatile @Nullable Thread worker;

	/**
	 * 创建消费端.
	 * @param messageSource 消息来源
	 * @param dispatcher 事件分发器
	 * @param properties canal 配置,提供拉取超时、失败退避与是否自动启动
	 */
	protected AbstractCanalEventConsumer(CanalMessageSource messageSource, CanalEventDispatcher dispatcher,
			CanalProperties properties) {
		this.messageSource = messageSource;
		this.dispatcher = dispatcher;
		this.properties = properties;
	}

	/**
	 * 处理一批事件.
	 * <p>
	 * 抛出任何异常都表示本批处理失败,骨架会回滚整批;正常返回则表示本批处理成功,骨架会确认整批。
	 * @param events 本批事件,必定非空
	 */
	protected abstract void process(List<CanalEvent> events);

	/**
	 * 消费方式名称,仅用于日志与线程命名.
	 * @return {@code sync} 或 {@code async}
	 */
	protected abstract String consumerType();

	/**
	 * 事件分发器,供子类处理事件时使用.
	 * @return 分发器
	 */
	protected final CanalEventDispatcher dispatcher() {
		return this.dispatcher;
	}

	/**
	 * 启动消费:在守护线程里开始拉取,重复调用不会启动第二个线程.
	 */
	@Override
	public final void start() {
		if (!this.running.compareAndSet(false, true)) {
			log.debug("Canal {} consumer is already running", consumerType());
			return;
		}

		Thread thread = new Thread(this::consume, "butterfly-canal-" + consumerType() + "-consumer");
		thread.setDaemon(true);
		this.worker = thread;
		thread.start();
		log.info("Canal {} consumer started", consumerType());
	}

	/**
	 * 停止消费:置停止标志、唤醒阻塞中的拉取,并等待后台线程退出.
	 * <p>
	 * 后台线程正是当前线程时(例如处理器里关闭了容器)不等待自己,避免自锁。
	 */
	@Override
	public final void stop() {
		if (!this.running.compareAndSet(true, false)) {
			return;
		}

		this.messageSource.wakeup();
		Thread thread = this.worker;
		if (thread != null && thread != Thread.currentThread()) {
			join(thread);
		}
		this.worker = null;
		log.info("Canal {} consumer stopped", consumerType());
	}

	@Override
	public final boolean isRunning() {
		return this.running.get();
	}

	@Override
	public final boolean isAutoStartup() {
		return this.properties.isAutoStartup();
	}

	/**
	 * 停止并回调,供 Spring 的生命周期处理器使用.
	 * @param callback 停止完成后的回调
	 */
	@Override
	public final void stop(Runnable callback) {
		stop();
		callback.run();
	}

	private void consume() {
		try {
			while (this.running.get()) {
				try {
					if (!this.connected && !connect()) {
						continue;
					}
					pollAndProcess();
				}
				catch (RuntimeException ex) {
					// 拉取、处理、确认各自的失败已在内部处理,这里只兜住意料之外的运行时异常
					onFailure("consume canal events", ex);
				}
			}
		}
		finally {
			this.running.set(false);
			this.connected = false;
			closeQuietly();
			log.info("Canal {} consumer thread exited", consumerType());
		}
	}

	private boolean connect() {
		try {
			this.messageSource.connect();
			this.connected = true;
			return true;
		}
		catch (RuntimeException ex) {
			onFailure("connect the canal " + consumerType() + " consumer", ex);
			return false;
		}
	}

	private void pollAndProcess() {
		List<CanalEvent> events;
		try {
			events = this.messageSource.poll(timeout());
		}
		catch (RuntimeException ex) {
			onFailure("poll canal events", ex);
			return;
		}

		if (events.isEmpty()) {
			return;
		}

		try {
			process(events);
		}
		catch (RuntimeException ex) {
			rollbackQuietly(ex);
			onFailure("process canal events", ex);
			return;
		}

		try {
			this.messageSource.ack();
		}
		catch (RuntimeException ex) {
			onFailure("acknowledge canal events", ex);
		}
	}

	/**
	 * 回滚整批,并保留原始异常.
	 * <p>
	 * 回滚本身失败时把异常挂到原始异常上,既不掩盖原始失败原因,也不会让回滚错误阻止消费端退避重试。
	 * @param cause 处理失败的原始异常
	 */
	private void rollbackQuietly(Throwable cause) {
		try {
			this.messageSource.rollback();
			log.warn("Rolled back a batch of canal events: {}", describe(cause));
		}
		catch (RuntimeException ex) {
			cause.addSuppressed(ex);
			log.warn("Failed to roll back a batch of canal events: {}", describe(ex));
		}
	}

	/**
	 * 记录失败并按配置退避.
	 * <p>
	 * 正在停止时的失败只打 debug 日志:此时失败多半来自"拉取被唤醒打断",不是真正的错误,也不该再等待。
	 * @param action 失败的动作描述
	 * @param ex 失败原因
	 */
	private void onFailure(String action, RuntimeException ex) {
		if (!this.running.get()) {
			log.debug("Failed to {} while stopping: {}", action, describe(ex));
			return;
		}

		log.warn("Failed to {}: {}", action, describe(ex));
		backOff();
	}

	private void backOff() {
		Duration errorBackOff = this.properties.getErrorBackOff();
		if (errorBackOff.isZero() || errorBackOff.isNegative()) {
			return;
		}

		try {
			Thread.sleep(errorBackOff.toMillis());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private void closeQuietly() {
		try {
			this.messageSource.close();
		}
		catch (RuntimeException ex) {
			log.warn("Failed to close the canal message source: {}", describe(ex));
		}
	}

	private Duration timeout() {
		return this.properties.getTimeout();
	}

	private void join(Thread thread) {
		try {
			thread.join(STOP_TIMEOUT.toMillis());
			if (thread.isAlive()) {
				log.warn("Canal {} consumer thread did not stop within {}s", consumerType(), STOP_TIMEOUT.toSeconds());
			}
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * 取异常的简短描述,异步消费抛出的 {@link CompletionException} 会剥掉外层包装.
	 * @param ex 原始异常
	 * @return 根因的描述文本
	 */
	private static String describe(Throwable ex) {
		Throwable cause = (ex instanceof CompletionException && ex.getCause() != null) ? ex.getCause() : ex;
		return cause.getClass().getSimpleName() + ": " + cause.getMessage();
	}

}
