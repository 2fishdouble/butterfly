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

package io.github.butterfly.autoconfigure;

import io.github.butterfly.core.BaseEnum;
import org.jspecify.annotations.Nullable;

/**
 * {@link BaseEnum} 枚举的解析工具:把外部传入的原始值解析为对应的枚举常量.
 * <p>
 * 匹配顺序为:先比较 {@link BaseEnum#getCode()} 的字符串形式(区分大小写),再忽略大小写地 比较
 * {@link BaseEnum#getTitle()} 与枚举常量名 {@link Enum#name()},返回第一个命中的常量。
 * <p>
 * 该类为工具类,不允许实例化。
 */
public final class BaseEnumJsonFactory {

	/**
	 * 私有构造器,禁止实例化工具类.
	 */
	private BaseEnumJsonFactory() {
	}

	/**
	 * 将原始值解析为指定类型的枚举常量.
	 * <p>
	 * 原始值先经 {@link String#valueOf(Object)} 转为字符串并去除首尾空白,再按 code、
	 * title、常量名的顺序逐个比对;转换后为空字符串时视为未提供值。
	 * @param <T> 目标枚举类型
	 * @param enumType 目标枚举类型,必须是枚举类
	 * @param rawValue 待解析的原始值,可为 {@code null}
	 * @return 匹配到的枚举常量;{@code rawValue} 为 {@code null} 或转换后为空字符串时返回 {@code null}
	 * @throws IllegalArgumentException {@code enumType} 不是枚举类,或没有任何常量的 code、title、
	 * 常量名与给定值匹配
	 */
	@SuppressWarnings("unchecked")
	@Nullable public static <T extends BaseEnum> T parse(Class<? extends BaseEnum> enumType, @Nullable Object rawValue) {
		if (rawValue == null) {
			return null;
		}

		String value = String.valueOf(rawValue).trim();
		if (value.isEmpty()) {
			return null;
		}

		if (!enumType.isEnum()) {
			throw new IllegalArgumentException("类型 %s 不是枚举".formatted(enumType.getName()));
		}

		T parsed = (T) BaseEnum.resolve(enumType, value);
		if (parsed == null) {
			throw new IllegalArgumentException("无法将值 [%s] 解析为枚举 %s".formatted(value, enumType.getSimpleName()));
		}

		return parsed;
	}

}
