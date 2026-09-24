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
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.StringUtils;

/**
 * 表名与库名的解析规则:先看 {@link CanalTable},再回落到实体类简单名.
 * <p>
 * 泛型驱动的 {@link CanalRowHandler} 与注解驱动的 {@link CanalListener} 共用这里的规则,因此两种写法的匹配 结果一致。
 */
public final class CanalTableResolver {

	private CanalTableResolver() {
	}

	/**
	 * 解析实体类对应的表名.
	 * @param type 实体类,通常来自 {@link CanalRowHandler} 的泛型参数或 {@link CanalListener} 方法的参数类型
	 * @return {@link CanalTable#value()} 非空时的取值,否则为实体类简单名首字母小写
	 */
	public static String resolveTable(Class<?> type) {
		CanalTable canalTable = AnnotatedElementUtils.findMergedAnnotation(type, CanalTable.class);
		if (canalTable != null && StringUtils.hasText(canalTable.value())) {
			return canalTable.value();
		}

		return uncapitalize(type.getSimpleName());
	}

	/**
	 * 解析实体类对应的库名.
	 * @param type 实体类,通常来自 {@link CanalRowHandler} 的泛型参数或 {@link CanalListener} 方法的参数类型
	 * @return {@link CanalTable#schema()} 非空时的取值,否则为 {@code null},表示不限库名
	 */
	public static @Nullable String resolveSchema(Class<?> type) {
		CanalTable canalTable = AnnotatedElementUtils.findMergedAnnotation(type, CanalTable.class);
		if (canalTable != null && StringUtils.hasText(canalTable.schema())) {
			return canalTable.schema();
		}

		return null;
	}

	private static String uncapitalize(String value) {
		if (value.isEmpty()) {
			return value;
		}
		return Character.toLowerCase(value.charAt(0)) + value.substring(1);
	}

}
