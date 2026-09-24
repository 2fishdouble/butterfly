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
import org.jspecify.annotations.Nullable;

/**
 * 框架关心的三类 binlog 行变更事件.
 * <p>
 * canal 其实还会推送 CREATE、ALTER、QUERY、TRUNCATE 等事件,它们要么不携带行数据(DDL),要么无法映射成 行变更,因此本枚举只保留
 * INSERT、UPDATE、DELETE,其余事件在解析阶段被丢弃。
 * <p>
 * {@link CanalEventType} 既用于 {@link CanalListener#events()} 声明处理器关心的事件,也用于
 * {@link CanalEvent#eventType()} 描述当前事件。
 */
public enum CanalEventType {

	/**
	 * 新增行,行数据取 canal 的 after 列.
	 */
	INSERT,

	/**
	 * 更新行,行数据取 canal 的 after 列,{@link CanalEvent#before()} 取 before 列.
	 * <p>
	 * 注意 canal 的 before 列默认只包含<b>被修改</b>的列,而不是整行旧值。
	 */
	UPDATE,

	/**
	 * 删除行,行数据取 canal 的 before 列(删除时 after 列为空).
	 */
	DELETE;

	/**
	 * 把 canal 的 protobuf 事件类型映射成本枚举.
	 * @param eventType canal 的 {@code CanalEntry.EventType};为空时返回 {@code null}
	 * @return 对应的事件类型;非 INSERT、UPDATE、DELETE 时返回 {@code null}
	 */
	public static @Nullable CanalEventType of(CanalEntry.@Nullable EventType eventType) {
		if (eventType == null) {
			return null;
		}

		return switch (eventType) {
			case INSERT -> INSERT;
			case UPDATE -> UPDATE;
			case DELETE -> DELETE;
			default -> null;
		};
	}

}
