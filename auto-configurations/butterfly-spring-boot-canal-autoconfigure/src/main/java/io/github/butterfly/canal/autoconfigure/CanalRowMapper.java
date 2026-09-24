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

import java.util.Map;

/**
 * 行数据映射器:把 canal 给出的"列名到列值"映射转换成实体对象.
 * <p>
 * 泛型驱动的 {@link CanalRowHandler} 与注解驱动的 {@link CanalListener} 都通过本接口把 {@link Map}
 * 形式的行数据变成实体,默认实现是 {@link BeanWrapperCanalRowMapper};需要自定义转换(例如处理 JSON 列、加密列或
 * 不支持的类型)时,自行注册一个 {@link CanalRowMapper} Bean 即可整体替换。
 */
public interface CanalRowMapper {

	/**
	 * 把一行数据映射成实体对象.
	 * @param row 列名到列值的映射,列值为 canal 给出的字符串形式
	 * @param type 目标实体类型,需要有默认构造方法与 setter
	 * @param <T> 目标实体类型
	 * @return 映射后的实体对象,必定非空
	 */
	<T> T map(Map<String, String> row, Class<T> type);

}
