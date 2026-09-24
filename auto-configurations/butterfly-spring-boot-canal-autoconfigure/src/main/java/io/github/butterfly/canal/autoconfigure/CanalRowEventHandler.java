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

import java.util.Map;

/**
 * 泛型驱动的处理器适配器:把 {@link CanalRowHandler} 的一个事件类型包装成 {@link CanalEventHandler}.
 * <p>
 * 每次处理都先把行数据映射成实体再调用对应方法:INSERT 调 {@link CanalRowHandler#insert(Object)},UPDATE 调
 * {@link CanalRowHandler#update(Object, Object)}(旧值可能为 {@code null}),DELETE 调
 * {@link CanalRowHandler#delete(Object)}。
 * <p>
 * 由于泛型参数在运行期被擦除,这里持有的是 {@code CanalRowHandler<Object>} 与 {@code Class<Object>},两者的类型 参数由
 * {@link CanalHandlerRegistrar} 从处理器 Bean 的泛型参数解析而来,保证一致。
 */
public class CanalRowEventHandler implements CanalEventHandler {

	private final CanalRowHandler<Object> handler;

	private final Class<Object> rowType;

	private final String table;

	private final @Nullable String schema;

	private final CanalEventType eventType;

	private final CanalRowMapper rowMapper;

	/**
	 * 创建适配器.
	 * @param handler 泛型驱动的处理器,其泛型参数与 {@code rowType} 一致
	 * @param rowType 行数据映射的目标类型,必定非空
	 * @param table 目标表名,必定非空
	 * @param schema 目标库名,{@code null} 表示不限库名
	 * @param eventType 本适配器负责的事件类型
	 * @param rowMapper 行数据映射器
	 */
	public CanalRowEventHandler(CanalRowHandler<Object> handler, Class<Object> rowType, String table,
			@Nullable String schema, CanalEventType eventType, CanalRowMapper rowMapper) {
		this.handler = handler;
		this.rowType = rowType;
		this.table = table;
		this.schema = schema;
		this.eventType = eventType;
		this.rowMapper = rowMapper;
	}

	@Override
	public @Nullable String schema() {
		return this.schema;
	}

	@Override
	public String table() {
		return this.table;
	}

	@Override
	public CanalEventType eventType() {
		return this.eventType;
	}

	@Override
	public void handle(CanalEvent event) {
		Object row = this.rowMapper.map(event.row(), this.rowType);
		switch (this.eventType) {
			case INSERT -> this.handler.insert(row);
			case UPDATE -> this.handler.update(row, mapBefore(event));
			case DELETE -> this.handler.delete(row);
		}
	}

	private @Nullable Object mapBefore(CanalEvent event) {
		Map<String, String> before = event.before();
		return (before != null) ? this.rowMapper.map(before, this.rowType) : null;
	}

}
