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

/**
 * 泛型驱动的行变更处理器:实现本接口的 Bean 会被自动注册,泛型参数即行数据映射的实体类.
 * <p>
 * 例如 {@code class ComputerRowHandler implements CanalRowHandler<Computer>} 会收到
 * {@code butterfly.computer} 表(或 {@code Computer} 上 {@link CanalTable} 指定的表)的
 * INSERT、UPDATE、 DELETE 事件,行数据已由 {@link CanalRowMapper} 转换成
 * {@code Computer}。三个方法都有空实现,只重写关心的 事件即可: <pre>{@code
 * &#64;Component
 * class ComputerRowHandler implements CanalRowHandler<Computer> {
 *

 *     &#64;Override
 *     public void insert(Computer row) {
 *         // 处理新增
 *     }
 *
 *
&#64;Override
 *     public void delete(Computer row) {
 *         // 处理删除,row 是删除前的整行
 *     }
 * }
 * }</pre>
 * <p>
 * 表名与库名默认取泛型参数上 {@link CanalTable} 的声明,未标注时表名为实体类简单名首字母小写、库名不限; 实体类来自第三方无法标注时重写
 * {@link #table()} 与 {@link #schema()} 覆盖。
 * <p>
 * 接口的泛型参数必须能解析成具体类型,否则注册时直接报错。
 *
 * @param <T> 行数据映射的目标类型,需要有默认构造方法与 setter
 */
public interface CanalRowHandler<T> {

	/**
	 * 处理新增行.
	 * @param row 新增后的整行数据
	 */
	default void insert(T row) {
	}

	/**
	 * 处理更新行.
	 * @param row 更新后的行数据
	 * @param before 更新前的旧值;canal 默认只包含被修改的列,可能是 {@code null} 或只有少数字段有值
	 */
	default void update(T row, @Nullable T before) {
	}

	/**
	 * 处理删除行.
	 * @param row 删除前的整行数据
	 */
	default void delete(T row) {
	}

	/**
	 * 覆盖表名.
	 * @return 表名;返回 {@code null} 时使用泛型参数上 {@link CanalTable} 的声明,或在实体类简单名首字母小写
	 */
	default @Nullable String table() {
		return null;
	}

	/**
	 * 覆盖库名.
	 * @return 库名;返回 {@code null} 时使用泛型参数上 {@link CanalTable} 的声明,或匹配任意库名
	 */
	default @Nullable String schema() {
		return null;
	}

}
