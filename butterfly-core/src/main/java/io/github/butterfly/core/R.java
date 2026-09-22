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

package io.github.butterfly.core;

import lombok.Data;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;

/**
 * 统一响应包装, 用于把接口结果包装为固定的 JSON 结构返回.
 * <p>
 * 约定:{@code code} 为 {@code 200} 表示成功、{@code 100000} 表示失败;成功时 {@code data} 携带业务数据, 失败时
 * {@code msg} 携带错误描述。构造成功/失败响应分别使用 {@link #success(Object)} 与 {@link #error(String)}, 也可用
 * {@link #R(Object)} 直接构造成功响应.
 *
 * @param <T> 业务数据类型
 */
@Data
public class R<T> implements Serializable {

	/**
	 * 业务状态码, 成功为 {@code 200}, 失败为 {@code 100000}.
	 */
	private int code;

	/**
	 * 是否成功标记, 与 {@code code} 对应:成功为 {@code true}, 失败为 {@code false}.
	 */
	private @Nullable Boolean isSuccess;

	/**
	 * 错误描述, 失败时由 {@link #error(String)} 写入;成功时不会被赋值.
	 */
	private @Nullable String msg;

	/**
	 * 业务数据, 成功时由 {@link #success(Object)} 或 {@link #R(Object)} 写入;失败时不会被赋值.
	 */
	private @Nullable T data;

	/**
	 * 构造成功响应并携带业务数据, 等价于 {@code code=200}、{@code isSuccess=true}.
	 * @param t 业务数据
	 * @param <T> 业务数据类型
	 * @return 成功响应, {@code msg} 不赋值
	 */
	public static <T> R<T> success(T t) {
		R<T> resultBase = new R<>();
		resultBase.setCode(200);
		resultBase.setIsSuccess(true);
		resultBase.setData(t);
		return resultBase;
	}

	/**
	 * 构造失败响应并携带错误描述, 等价于 {@code code=100000}、{@code isSuccess=false}.
	 * @param errorInfo 错误描述, 写入 {@code msg};{@code data} 不赋值
	 * @param <T> 业务数据类型
	 * @return 失败响应
	 */
	public static <T> R<T> error(String errorInfo) {
		R<T> resultBase = new R<>();
		resultBase.setCode(100000);
		resultBase.setIsSuccess(false);
		resultBase.setMsg(errorInfo);
		return resultBase;
	}

	/**
	 * 无参构造器, 各字段保持 Java 默认值({@code code} 为 {@code 0}, 其余字段未被赋值), 供先创建实例再逐字段赋值的场景使用.
	 */
	public R() {
	}

	/**
	 * 使用业务数据构造成功响应, 等价于 {@code code=200}、{@code isSuccess=true}.
	 * @param t 业务数据
	 */
	public R(T t) {
		this.code = 200;
		this.isSuccess = true;
		this.data = t;
	}

}
