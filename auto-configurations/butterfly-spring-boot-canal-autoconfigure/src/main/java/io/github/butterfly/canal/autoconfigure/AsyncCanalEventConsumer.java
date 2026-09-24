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

import org.springframework.core.task.AsyncTaskExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 异步消费:把整批事件提交到线程池并发处理,等全部完成后再确认.
 * <p>
 * 与同步消费的区别只在"怎么处理一批":
 * <ul>
 * <li>并发执行,吞吐随 {@code butterfly.canal.async.*} 的线程数提升,但同一批内的事件不再有序,需要顺序保证的场景 请用同步消费;</li>
 * <li>确认仍然是整批的:只要有一个事件失败,整批(包括已经成功的事件)都会回滚重投,因此处理器必须幂等;</li>
 * <li>等待方式是对所有任务做 {@code allOf(...).join()},因此失败时已经在跑的任务也会先跑完,不会留下"批已回滚但 任务还在跑"的窗口。</li>
 * </ul>
 * <p>
 * 由 {@code butterfly.canal.consumer-type=async} 启用;线程池是名为
 * {@code butterflyCanalAsyncExecutor} 的 {@code ThreadPoolTaskExecutor},使用方可以定义同名 Bean
 * 替换它。
 */
public class AsyncCanalEventConsumer extends AbstractCanalEventConsumer {

	private final AsyncTaskExecutor executor;

	/**
	 * 创建异步消费端.
	 * @param messageSource 消息来源
	 * @param dispatcher 事件分发器
	 * @param properties canal 配置
	 * @param executor 处理事件的线程池
	 */
	public AsyncCanalEventConsumer(CanalMessageSource messageSource, CanalEventDispatcher dispatcher,
			CanalProperties properties, AsyncTaskExecutor executor) {
		super(messageSource, dispatcher, properties);
		this.executor = executor;
	}

	/**
	 * 并发分发本批事件,等全部完成;任一处理器抛出异常时本方法抛出 {@link java.util.concurrent.CompletionException},
	 * 骨架据此回滚整批.
	 * @param events 本批事件
	 */
	@Override
	protected void process(List<CanalEvent> events) {
		List<CompletableFuture<Void>> futures = new ArrayList<>(events.size());
		for (CanalEvent event : events) {
			futures.add(CompletableFuture.runAsync(() -> dispatcher().dispatch(event), this.executor));
		}

		CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
	}

	@Override
	protected String consumerType() {
		return "async";
	}

}
