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

/**
 * 消息来源:向消费端暴露"拉取—确认—回滚"三段式接口,与具体的 canal 连接方式解耦.
 * <p>
 * 生命周期由消费端驱动:{@link #connect()} 建连接与订阅,随后反复 {@link #poll(Duration)},处理成功调
 * {@link #ack()}、失败调 {@link #rollback()},停止时先 {@link #wakeup()} 打断阻塞中的 {@code poll},最后由
 * {@code poll} 所在线程 {@link #close()}。
 * <p>
 * <b>线程约束</b>:除 {@link #wakeup()} 外的方法都只在拉取线程上调用,实现不需要考虑并发(canal 的连接器本身也不是 线程安全的)。
 * <p>
 * 需要接入 canal 的其它连接方式(例如集群、MQ)时,实现本接口并注册为 Bean 即可替换默认实现。
 */
public interface CanalMessageSource {

	/**
	 * 建立连接并订阅.
	 * <p>
	 * 由拉取线程在开始拉取前调用一次;实现应当把连接与订阅都放在这里,而不是构造方法里,这样应用启动阶段不会因为 canal server 不可用而失败。
	 */
	void connect();

	/**
	 * 拉取一批事件.
	 * @param timeout 等待数据的超时时间
	 * @return 本次拉取到的事件列表,没有数据时返回空列表;返回结果在 {@link #ack()} 或 {@link #rollback()} 之前都还可以重新投递
	 */
	List<CanalEvent> poll(Duration timeout);

	/**
	 * 确认本批事件已处理完成.
	 * <p>
	 * 按 canal 的 batchId 确认,确认之后小于等于该 batchId 的消息都不会再投递。
	 */
	void ack();

	/**
	 * 回滚本批事件,让它们下次重新投递.
	 */
	void rollback();

	/**
	 * 打断阻塞中的 {@link #poll(Duration)},用于停止消费.
	 * <p>
	 * 默认实现什么都不做:只要 {@code poll} 的超时不是无限(见 {@code butterfly.canal.timeout}),拉取循环会在
	 * 超时后自行发现停止标志;需要立刻打断阻塞中 {@code poll} 的实现可以覆盖它。本方法可能被停止线程调用,实现必须是 线程安全的。
	 */
	default void wakeup() {
	}

	/**
	 * 释放连接.
	 * <p>
	 * 由拉取线程在退出前调用,实现里不应抛出异常。
	 */
	void close();

}
