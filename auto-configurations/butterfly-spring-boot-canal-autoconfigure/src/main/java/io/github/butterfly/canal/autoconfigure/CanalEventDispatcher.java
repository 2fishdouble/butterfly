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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 事件分发器:按"库名 + 表名 + 事件类型"把 {@link CanalEvent} 路由给已注册的 {@link CanalEventHandler}.
 * <p>
 * 库名为 {@code null} 的处理器是通配处理器,匹配任意库;同一个三元组上可以注册多个处理器,它们按注册顺序依次
 * 执行,任一处理器抛出异常都会中断本事件的分发并向上传播,由消费端决定回滚与重试。
 * <p>
 * 无匹配处理器的事件会被静默丢弃(只打 debug 日志),因为 canal 会推送整库整表的变更,而使用方通常只关心其中 少数表。注册与分发都是线程安全的:处理器存放在
 * {@link CopyOnWriteArrayList} 中,因此异步消费时并发分发不会 抛并发修改异常。
 */
public class CanalEventDispatcher {

	private static final Logger log = LoggerFactory.getLogger(CanalEventDispatcher.class);

	private final Map<HandlerKey, List<CanalEventHandler>> handlers = new ConcurrentHashMap<>();

	/**
	 * 注册一个处理器.
	 * @param handler 处理器,键取自
	 * {@link CanalEventHandler#schema()}、{@link CanalEventHandler#table()} 与
	 * {@link CanalEventHandler#eventType()}
	 */
	public void register(CanalEventHandler handler) {
		HandlerKey key = new HandlerKey(handler.schema(), handler.table(), handler.eventType());
		this.handlers.computeIfAbsent(key, (unused) -> new CopyOnWriteArrayList<>()).add(handler);
		log.debug("Registered canal handler [{}] on table {}.{}", handler.getClass().getSimpleName(), handler.schema(),
				handler.table());
	}

	/**
	 * 把事件分发给所有匹配的处理器.
	 * @param event 行变更事件
	 * @throws RuntimeException 任一处理器抛出异常时原样向上抛出,后续处理器不再执行
	 */
	public void dispatch(CanalEvent event) {
		List<CanalEventHandler> matched = findHandlers(event);
		if (matched.isEmpty()) {
			log.debug("No canal handler registered for {} on table {}.{}", event.eventType(), event.schema(),
					event.table());
			return;
		}

		for (CanalEventHandler handler : matched) {
			handler.handle(event);
		}
	}

	/**
	 * 已注册的处理器总数.
	 * @return 处理器个数,用于启动日志与自检
	 */
	public int handlerCount() {
		return this.handlers.values().stream().mapToInt(List::size).sum();
	}

	private List<CanalEventHandler> findHandlers(CanalEvent event) {
		List<CanalEventHandler> matched = new ArrayList<>();
		CanalEventType eventType = event.eventType();
		String table = event.table();
		String schema = event.schema();

		if (schema != null) {
			matched.addAll(this.handlers.getOrDefault(new HandlerKey(schema, table, eventType), List.of()));
		}
		matched.addAll(this.handlers.getOrDefault(new HandlerKey(null, table, eventType), List.of()));
		return matched;
	}

	/**
	 * 处理器键:库名为 {@code null} 表示通配.
	 *
	 * @param schema 库名,可为空
	 * @param table 表名
	 * @param eventType 事件类型
	 */
	private record HandlerKey(@Nullable String schema, String table, CanalEventType eventType) {
	}

}
