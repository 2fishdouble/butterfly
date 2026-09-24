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
import com.alibaba.otter.canal.protocol.exception.CanalClientException;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * canal 消息到 {@link CanalEvent} 的转换器:把一条 canal 消息拆成单行粒度的事件.
 * <p>
 * 直连 canal server 拿到的是 protobuf 的 {@link Message},这里按 {@link CanalEntry.Entry} 逐条解析:非
 * {@code ROWDATA} 条目(事务开始/结束、心跳)与 DDL 条目直接跳过,懒解析({@link Message#isRaw()})时逐个解析 原始字节。
 * <p>
 * 行数据的归属规则见 {@link CanalEvent}:INSERT 取 after 列、UPDATE 取 after 列并以 before 列作为旧值、DELETE 取
 * before 列;值为 {@code NULL} 的列会被跳过。
 */
public final class CanalEventConverter {

	private static final Logger log = LoggerFactory.getLogger(CanalEventConverter.class);

	private CanalEventConverter() {
	}

	/**
	 * 把 protobuf 消息转换成事件列表.
	 * @param destination canal 实例名,写进每个事件的 {@link CanalEvent#destination()}
	 * @param message canal 消息,{@link Message#isRaw()} 为 {@code true} 时按懒解析处理
	 * @return 单行粒度的事件列表,没有行变更时为空列表
	 * @throws CanalClientException 懒解析的字节无法解析成 {@link CanalEntry.Entry} 时抛出
	 */
	public static List<CanalEvent> fromMessage(String destination, Message message) {
		List<CanalEvent> events = new ArrayList<>();
		if (message.isRaw()) {
			for (ByteString rawEntry : message.getRawEntries()) {
				addEntry(destination, parseEntry(rawEntry), events);
			}
		}
		else {
			for (CanalEntry.Entry entry : message.getEntries()) {
				addEntry(destination, entry, events);
			}
		}
		return events;
	}

	private static void addEntry(String destination, CanalEntry.Entry entry, List<CanalEvent> events) {
		if (entry.getEntryType() != CanalEntry.EntryType.ROWDATA) {
			return;
		}

		CanalEntry.RowChange rowChange = parseRowChange(entry);
		if (rowChange.getIsDdl()) {
			log.debug("Skip DDL entry on table {}", entry.getHeader().getTableName());
			return;
		}

		CanalEventType eventType = CanalEventType.of(rowChange.getEventType());
		if (eventType == null) {
			log.debug("Skip canal entry of unsupported event type {}", rowChange.getEventType());
			return;
		}

		CanalEntry.Header header = entry.getHeader();
		for (CanalEntry.RowData rowData : rowChange.getRowDatasList()) {
			events.add(createEvent(destination, header.getSchemaName(), header.getTableName(), eventType,
					columns(rowData.getAfterColumnsList()), columns(rowData.getBeforeColumnsList()), rowChange.getSql(),
					header.getExecuteTime()));
		}
	}

	/**
	 * 组装单行事件并决定行数据取自 before 还是 after.
	 * <p>
	 * 删除时 canal 的 after 列为空、行数据在 before 列,因此这里按 after 是否为空决定取值方向:after 有值就取
	 * after(INSERT、UPDATE),after 为空就回落到 before(DELETE)。
	 * @param destination canal 实例名
	 * @param schema 库名,可为空
	 * @param table 表名
	 * @param eventType 事件类型
	 * @param after after 列
	 * @param before before 列
	 * @param sql 触发变更的 SQL,可为空
	 * @param executeTimeMillis 执行时间,可为空
	 * @return 单行事件
	 */
	private static CanalEvent createEvent(String destination, @Nullable String schema, String table,
			CanalEventType eventType, Map<String, String> after, Map<String, String> before, @Nullable String sql,
			@Nullable Long executeTimeMillis) {
		Map<String, String> row = after.isEmpty() ? before : after;
		Map<String, String> previous = (eventType == CanalEventType.UPDATE && !before.isEmpty()) ? before : null;

		return new CanalEvent(destination, schema, table, eventType, row, previous, sql,
				Instant.ofEpochMilli((executeTimeMillis != null) ? executeTimeMillis : 0L));
	}

	private static Map<String, String> columns(List<CanalEntry.Column> columns) {
		Map<String, String> values = new LinkedHashMap<>(columns.size());
		for (CanalEntry.Column column : columns) {
			if (!column.getIsNull()) {
				values.put(column.getName(), column.getValue());
			}
		}
		return values;
	}

	private static CanalEntry.Entry parseEntry(ByteString rawEntry) {
		try {
			return CanalEntry.Entry.parseFrom(rawEntry);
		}
		catch (InvalidProtocolBufferException ex) {
			throw new CanalClientException("Failed to parse canal entry: " + ex.getMessage(), ex);
		}
	}

	private static CanalEntry.RowChange parseRowChange(CanalEntry.Entry entry) {
		try {
			return CanalEntry.RowChange.parseFrom(entry.getStoreValue());
		}
		catch (InvalidProtocolBufferException ex) {
			throw new CanalClientException("Failed to parse canal row change on table "
					+ entry.getHeader().getTableName() + ": " + ex.getMessage(), ex);
		}
	}

}
