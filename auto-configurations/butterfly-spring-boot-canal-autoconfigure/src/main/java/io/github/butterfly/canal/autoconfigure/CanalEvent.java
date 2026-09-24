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

import java.time.Instant;
import java.util.Map;

/**
 * 一次行变更事件:一条 binlog 行变更对应一个 {@link CanalEvent}.
 * <p>
 * canal 的一条消息里可以包含多行变更(一个 {@code RowChange} 带多个 {@code RowData}),解析时会按行拆开,因此
 * 处理器拿到的永远是单行粒度的事件。
 * <p>
 * 行数据的归属规则如下,{@link #row()} 始终是"本次事件的目标行":
 * <ul>
 * <li>{@link CanalEventType#INSERT}:{@link #row()} 取 after 列,{@link #before()} 为
 * {@code null};</li>
 * <li>{@link CanalEventType#UPDATE}:{@link #row()} 取 after 列,{@link #before()} 取 before
 * 列(默认只含被修改的列);</li>
 * <li>{@link CanalEventType#DELETE}:{@link #row()} 取 before 列(删除时 after
 * 列为空),{@link #before()} 为 {@code null}。</li>
 * </ul>
 * <p>
 * 行数据里值为 {@code NULL} 的列会被跳过,因此"列不存在"与"列的值为 NULL"在 {@link #row()} 中无法区分,实体字段 保持默认值。
 *
 * @param destination canal 实例名(destination),来自 {@code butterfly.canal.destination}
 * @param schema 库名,即 MySQL 的 database 名;来自 canal 消息头里的 schemaName
 * @param table 表名
 * @param eventType 事件类型
 * @param row 本次事件的目标行,列名到列值的映射,必定非空(可能为空映射)
 * @param before UPDATE 事件的旧值,列名到列值的映射;其它事件为 {@code null}
 * @param sql 触发本次变更的 SQL,canal 未采集到时为 {@code null} 或空串
 * @param executeTime 变更在源库上的执行时间
 */
public record CanalEvent(String destination, @Nullable String schema, String table, CanalEventType eventType,
		Map<String, String> row, @Nullable Map<String, String> before, @Nullable String sql, Instant executeTime) {
}
