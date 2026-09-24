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

/**
 * 事件消费端:负责在后台线程里从 {@link CanalMessageSource} 拉取事件、交给 {@link CanalEventDispatcher}
 * 处理,并按处理结果确认或回滚.
 * <p>
 * 实现同时是 Spring 的 {@code SmartLifecycle}:{@code butterfly.canal.auto-startup} 为
 * {@code true} (默认)时随应用启动自动开始消费,关闭时自动停止;为 {@code false} 时也可由使用方自行调用 {@link #start()}。
 * <p>
 * 两种实现对应 {@code butterfly.canal.consumer-type} 的两个取值,见 {@link SyncCanalEventConsumer} 与
 * {@link AsyncCanalEventConsumer}。
 */
public interface CanalEventConsumer {

	/**
	 * 启动消费:在后台线程里开始拉取数据.
	 * <p>
	 * 已经启动时重复调用不会启动第二个线程。
	 */
	void start();

	/**
	 * 停止消费并等待后台线程退出.
	 * <p>
	 * 未启动时调用本方法什么都不做。停止前如果还有未确认的批次,该批次留在 canal server 侧等待下次投递,不会丢失。
	 */
	void stop();

	/**
	 * 消费线程是否正在运行.
	 * @return 正在拉取时返回 {@code true}
	 */
	boolean isRunning();

}
