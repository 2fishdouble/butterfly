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

import lombok.Data;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 校验两种处理器写法都能被注册并按库名、表名、事件类型正确分发,包括实体映射(下划线转驼峰、日期时间)与配置错误 的快速失败,全程不触网。
 */
class CanalEventDispatcherTests {

	private final CanalEventDispatcher dispatcher = new CanalEventDispatcher();

	private final List<String> handled = new ArrayList<>();

	@Test
	void dispatchesGenericHandlerForEveryEventType() {
		register(new ComputerRowHandler(this.handled));

		dispatch(CanalEventType.INSERT, row("id", "1", "name", "pc", "create_time", "2024-01-01 10:00:00"));
		dispatch(CanalEventType.UPDATE, row("id", "1", "name", "new"), row("name", "old"));
		dispatch(CanalEventType.DELETE, row("id", "1", "name", "pc"));

		assertThat(this.handled).containsExactly("insert:1:pc:2024-01-01T10:00", "update:new:old", "delete:1");
	}

	@Test
	void dispatchesListenerMethodsForEveryEventType() {
		register(new ComputerListener(this.handled));

		dispatch(CanalEventType.INSERT, row("id", "1", "name", "pc"));
		dispatch(CanalEventType.UPDATE, row("id", "1", "name", "new"), row("name", "old"));
		dispatch(CanalEventType.DELETE, row("id", "1"));

		assertThat(this.handled).containsExactly("listener-insert:pc", "listener-update:new:old", "listener-delete:1");
	}

	@Test
	void dispatchesBothStylesWhenTheyAreRegisteredOnTheSameTable() {
		register(new ComputerRowHandler(this.handled), new ComputerListener(this.handled));

		dispatch(CanalEventType.INSERT, row("id", "1", "name", "pc"));

		assertThat(this.handled).containsExactlyInAnyOrder("insert:1:pc:null", "listener-insert:pc");
	}

	/**
	 * 未标注 {@link CanalTable} 的实体,表名取简单名首字母小写,库名不限。
	 */
	@Test
	void resolvesTableNameFromTheEntitySimpleNameAndMatchesAnySchema() {
		register(new OrderRowHandler(this.handled));

		this.dispatcher.dispatch(event("other", "order", CanalEventType.INSERT, row("id", "9"), null));

		assertThat(this.handled).containsExactly("order-insert:9");
	}

	/**
	 * 标了 {@link CanalTable#schema()} 的处理器只接自己库的事件。
	 */
	@Test
	void ignoresEventsFromOtherSchemasWhenSchemaIsDeclared() {
		register(new ComputerRowHandler(this.handled));

		this.dispatcher.dispatch(event("other", "computer", CanalEventType.INSERT, row("id", "1", "name", "pc"), null));

		assertThat(this.handled).isEmpty();
	}

	@Test
	void ignoresEventsOfUnhandledTables() {
		register(new ComputerRowHandler(this.handled));

		this.dispatcher.dispatch(event("butterfly", "monitor", CanalEventType.INSERT, row("id", "1"), null));

		assertThat(this.handled).isEmpty();
		assertThat(this.dispatcher.handlerCount()).isEqualTo(3);
	}

	@Test
	void rejectsHandlersWhoseGenericTypeCannotBeResolved() {
		assertThatThrownBy(() -> register(new RawRowHandler())).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Cannot resolve the row type");
	}

	@Test
	void rejectsListenerMethodsWithoutResolvableTable() {
		assertThatThrownBy(() -> register(new EventOnlyListener(this.handled)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Cannot resolve the target table");
	}

	@Test
	void rejectsListenerMethodsWithMoreThanTwoParameters() {
		assertThatThrownBy(() -> register(new TooManyParametersListener(this.handled)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("must not declare more than two parameters");
	}

	private void register(Object... beans) {
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
		for (int index = 0; index < beans.length; index++) {
			beanFactory.registerSingleton("bean" + index, beans[index]);
		}

		new CanalHandlerRegistrar(beanFactory, this.dispatcher, new BeanWrapperCanalRowMapper())
			.afterSingletonsInstantiated();
	}

	private void dispatch(CanalEventType eventType, Map<String, String> row) {
		dispatch(eventType, row, null);
	}

	private void dispatch(CanalEventType eventType, Map<String, String> row, @Nullable Map<String, String> before) {
		this.dispatcher.dispatch(event("butterfly", "computer", eventType, row, before));
	}

	private static CanalEvent event(String schema, String table, CanalEventType eventType, Map<String, String> row,
			@Nullable Map<String, String> before) {
		return new CanalEvent("example", schema, table, eventType, row, before, null, Instant.EPOCH);
	}

	private static Map<String, String> row(String... keysAndValues) {
		Map<String, String> row = new LinkedHashMap<>();
		for (int index = 0; index < keysAndValues.length; index += 2) {
			row.put(keysAndValues[index], keysAndValues[index + 1]);
		}
		return row;
	}

	/**
	 * 事件实体:标了库名与表名.
	 */
	@CanalTable(value = "computer", schema = "butterfly")
	@Data
	static class Computer {

		private Long id;

		private String name;

		private LocalDateTime createTime;

	}

	/**
	 * 未标注 {@link CanalTable} 的事件实体,表名取 {@code order}.
	 */
	@Data
	static class Order {

		private Long id;

	}

	static class ComputerRowHandler implements CanalRowHandler<Computer> {

		private final List<String> handled;

		ComputerRowHandler(List<String> handled) {
			this.handled = handled;
		}

		@Override
		public void insert(Computer row) {
			this.handled.add("insert:" + row.getId() + ":" + row.getName() + ":" + row.getCreateTime());
		}

		@Override
		public void update(Computer row, @Nullable Computer before) {
			this.handled.add("update:" + row.getName() + ":" + ((before != null) ? before.getName() : "null"));
		}

		@Override
		public void delete(Computer row) {
			this.handled.add("delete:" + row.getId());
		}

	}

	static class OrderRowHandler implements CanalRowHandler<Order> {

		private final List<String> handled;

		OrderRowHandler(List<String> handled) {
			this.handled = handled;
		}

		@Override
		public void insert(Order row) {
			this.handled.add("order-insert:" + row.getId());
		}

	}

	static class ComputerListener {

		private final List<String> handled;

		ComputerListener(List<String> handled) {
			this.handled = handled;
		}

		@CanalListener(table = "computer", events = CanalEventType.INSERT)
		public void onInsert(Computer computer) {
			this.handled.add("listener-insert:" + computer.getName());
		}

		@CanalListener(table = "computer", events = CanalEventType.UPDATE)
		public void onUpdate(Computer computer, @Nullable Computer before) {
			this.handled
				.add("listener-update:" + computer.getName() + ":" + ((before != null) ? before.getName() : "null"));
		}

		@CanalListener(table = "computer", events = CanalEventType.DELETE)
		public void onDelete(CanalEvent event) {
			this.handled.add("listener-delete:" + event.row().get("id"));
		}

	}

	/**
	 * 泛型参数没写具体类型的处理器.
	 */
	static class RawRowHandler implements CanalRowHandler {

		@Override
		public void insert(Object row) {
		}

	}

	/**
	 * 第一个参数是 {@link CanalEvent} 且没有声明表名的方法.
	 */
	static class EventOnlyListener {

		private final List<String> handled;

		EventOnlyListener(List<String> handled) {
			this.handled = handled;
		}

		@CanalListener
		public void onEvent(CanalEvent event) {
			this.handled.add("event");
		}

	}

	static class TooManyParametersListener {

		private final List<String> handled;

		TooManyParametersListener(List<String> handled) {
			this.handled = handled;
		}

		@CanalListener(table = "computer")
		public void onEvent(CanalEvent event, Computer row, Computer before) {
			this.handled.add("event");
		}

	}

}
