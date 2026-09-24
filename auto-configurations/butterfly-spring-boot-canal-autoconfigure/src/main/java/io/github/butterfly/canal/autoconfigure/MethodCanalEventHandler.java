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
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * 注解驱动的处理器适配器:把一个标注 {@link CanalListener} 的方法包装成 {@link CanalEventHandler}.
 * <p>
 * 参数绑定规则见 {@link CanalListener};第二个参数只在 UPDATE 事件上注入旧值,其余事件注入 {@code null}。 目标方法抛出的异常由
 * {@link ReflectionUtils#invokeMethod} 原样抛出(受检异常包装成
 * {@code UndeclaredThrowableException}),交给消费端决定回滚与重试。
 */
public class MethodCanalEventHandler implements CanalEventHandler {

	private final Object bean;

	private final Method method;

	private final Class<?>[] parameterTypes;

	private final String table;

	private final @Nullable String schema;

	private final CanalEventType eventType;

	private final CanalRowMapper rowMapper;

	/**
	 * 创建适配器.
	 * @param bean 目标方法所属的 Bean
	 * @param method 标注了 {@link CanalListener} 的方法,参数个数不能超过两个
	 * @param table 目标表名,必定非空
	 * @param schema 目标库名,{@code null} 表示不限库名
	 * @param eventType 本适配器负责的事件类型
	 * @param rowMapper 行数据映射器,用于把行数据转换成方法参数类型
	 * @throws IllegalStateException 方法参数个数超过两个时抛出
	 */
	public MethodCanalEventHandler(Object bean, Method method, String table, @Nullable String schema,
			CanalEventType eventType, CanalRowMapper rowMapper) {
		this.parameterTypes = method.getParameterTypes();
		if (this.parameterTypes.length > 2) {
			throw new IllegalStateException("Canal listener method " + method + " must not declare more than two "
					+ "parameters; the first receives the row, the second receives the previous row of an update");
		}

		ReflectionUtils.makeAccessible(method);
		this.bean = bean;
		this.method = method;
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
		ReflectionUtils.invokeMethod(this.method, this.bean, resolveArguments(event));
	}

	private Object[] resolveArguments(CanalEvent event) {
		Object[] arguments = new Object[this.parameterTypes.length];
		if (this.parameterTypes.length > 0) {
			arguments[0] = resolveArgument(event, this.parameterTypes[0], event.row());
		}
		if (this.parameterTypes.length > 1) {
			boolean update = event.eventType() == CanalEventType.UPDATE;
			arguments[1] = (update) ? resolveArgument(event, this.parameterTypes[1], event.before()) : null;
		}
		return arguments;
	}

	private @Nullable Object resolveArgument(CanalEvent event, Class<?> parameterType,
			@Nullable Map<String, String> row) {
		if (CanalEvent.class.isAssignableFrom(parameterType)) {
			return event;
		}
		if (row == null) {
			return null;
		}
		if (Map.class.isAssignableFrom(parameterType)) {
			return row;
		}
		return this.rowMapper.map(row, parameterType);
	}

}
