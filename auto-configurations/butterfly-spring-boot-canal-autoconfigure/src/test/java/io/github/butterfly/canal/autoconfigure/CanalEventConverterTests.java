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

import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 校验 canal 的 protobuf 条目到 {@link CanalEvent} 的转换:事件类型、库表、行数据归属、NULL 列与 DDL 等跳过规则, 全程不触网。
 */
class CanalEventConverterTests {

	private static final String DESTINATION = "example";

	private static final long EXECUTE_TIME = 1700000000000L;

	@Test
	void convertsInsertEntry() {
		Message message = new Message(1L, List.of(insertEntry()));

		List<CanalEvent> events = CanalEventConverter.fromMessage(DESTINATION, message);

		assertThat(events).singleElement().satisfies((event) -> {
			assertThat(event.destination()).isEqualTo(DESTINATION);
			assertThat(event.schema()).isEqualTo("butterfly");
			assertThat(event.table()).isEqualTo("computer");
			assertThat(event.eventType()).isEqualTo(CanalEventType.INSERT);
			assertThat(event.row()).containsEntry("id", "1").containsEntry("name", "pc");
			assertThat(event.before()).isNull();
			assertThat(event.executeTime()).isEqualTo(Instant.ofEpochMilli(EXECUTE_TIME));
		});
	}

	@Test
	void keepsBeforeRowOnUpdate() {
		CanalEntry.RowData rowData = CanalEntry.RowData.newBuilder()
			.addBeforeColumns(column("name", "old"))
			.addAfterColumns(column("id", "1"))
			.addAfterColumns(column("name", "new"))
			.build();

		List<CanalEvent> events = CanalEventConverter.fromMessage(DESTINATION,
				new Message(1L, List.of(entry(CanalEntry.EventType.UPDATE, false, rowData))));

		assertThat(events).singleElement().satisfies((event) -> {
			assertThat(event.eventType()).isEqualTo(CanalEventType.UPDATE);
			assertThat(event.row()).containsEntry("name", "new");
			assertThat(event.before()).containsEntry("name", "old");
		});
	}

	@Test
	void takesBeforeRowAsRowOnDelete() {
		CanalEntry.RowData rowData = CanalEntry.RowData.newBuilder()
			.addBeforeColumns(column("id", "1"))
			.addBeforeColumns(column("name", "pc"))
			.build();

		List<CanalEvent> events = CanalEventConverter.fromMessage(DESTINATION,
				new Message(1L, List.of(entry(CanalEntry.EventType.DELETE, false, rowData))));

		assertThat(events).singleElement().satisfies((event) -> {
			assertThat(event.eventType()).isEqualTo(CanalEventType.DELETE);
			assertThat(event.row()).containsEntry("id", "1").containsEntry("name", "pc");
			assertThat(event.before()).isNull();
		});
	}

	@Test
	void skipsNullColumns() {
		CanalEntry.RowData rowData = CanalEntry.RowData.newBuilder()
			.addAfterColumns(column("id", "1"))
			.addAfterColumns(column("name", null))
			.build();

		List<CanalEvent> events = CanalEventConverter.fromMessage(DESTINATION,
				new Message(1L, List.of(entry(CanalEntry.EventType.INSERT, false, rowData))));

		assertThat(events).singleElement().satisfies((event) -> assertThat(event.row()).containsOnlyKeys("id"));
	}

	@Test
	void skipsEntriesThatAreNotRowChanges() {
		CanalEntry.Entry transactionBegin = CanalEntry.Entry.newBuilder()
			.setEntryType(CanalEntry.EntryType.TRANSACTIONBEGIN)
			.setHeader(header(CanalEntry.EventType.INSERT))
			.build();
		CanalEntry.Entry ddl = entry(CanalEntry.EventType.ALTER, true, CanalEntry.RowData.getDefaultInstance());
		CanalEntry.Entry query = entry(CanalEntry.EventType.QUERY, false, CanalEntry.RowData.getDefaultInstance());

		List<CanalEvent> events = CanalEventConverter.fromMessage(DESTINATION,
				new Message(1L, List.of(transactionBegin, ddl, query, insertEntry())));

		assertThat(events).singleElement()
			.satisfies((event) -> assertThat(event.eventType()).isEqualTo(CanalEventType.INSERT));
	}

	@Test
	void convertsLazilyParsedEntries() {
		Message message = new Message(1L);
		message.setRaw(true);
		message.setRawEntries(List.of(insertEntry().toByteString()));

		List<CanalEvent> events = CanalEventConverter.fromMessage(DESTINATION, message);

		assertThat(events).singleElement().satisfies((event) -> assertThat(event.row()).containsEntry("name", "pc"));
	}

	@Test
	void mapsCanalEventTypes() {
		assertThat(CanalEventType.of(CanalEntry.EventType.INSERT)).isEqualTo(CanalEventType.INSERT);
		assertThat(CanalEventType.of(CanalEntry.EventType.UPDATE)).isEqualTo(CanalEventType.UPDATE);
		assertThat(CanalEventType.of(CanalEntry.EventType.DELETE)).isEqualTo(CanalEventType.DELETE);
		assertThat(CanalEventType.of(CanalEntry.EventType.CREATE)).isNull();
		assertThat((@Nullable CanalEventType) null).isNull();
	}

	private static CanalEntry.Entry insertEntry() {
		CanalEntry.RowData rowData = CanalEntry.RowData.newBuilder()
			.addAfterColumns(column("id", "1"))
			.addAfterColumns(column("name", "pc"))
			.build();
		return entry(CanalEntry.EventType.INSERT, false, rowData);
	}

	private static CanalEntry.Entry entry(CanalEntry.EventType eventType, boolean ddl, CanalEntry.RowData rowData) {
		CanalEntry.RowChange rowChange = CanalEntry.RowChange.newBuilder()
			.setEventType(eventType)
			.setIsDdl(ddl)
			.addRowDatas(rowData)
			.build();

		return CanalEntry.Entry.newBuilder()
			.setEntryType(CanalEntry.EntryType.ROWDATA)
			.setHeader(header(eventType))
			.setStoreValue(rowChange.toByteString())
			.build();
	}

	private static CanalEntry.Header header(CanalEntry.EventType eventType) {
		return CanalEntry.Header.newBuilder()
			.setSchemaName("butterfly")
			.setTableName("computer")
			.setExecuteTime(EXECUTE_TIME)
			.setEventType(eventType)
			.build();
	}

	private static CanalEntry.Column column(String name, @Nullable String value) {
		CanalEntry.Column.Builder builder = CanalEntry.Column.newBuilder().setName(name).setIsNull(value == null);
		if (value != null) {
			builder.setValue(value);
		}
		return builder.build();
	}

}
