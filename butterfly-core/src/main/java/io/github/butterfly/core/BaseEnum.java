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

import org.jspecify.annotations.Nullable;

/**
 * 枚举统一接口, 实现本接口的枚举可被框架统一序列化与展示.
 * <p>
 * 约定行为:JSON 序列化时只输出 {@link #getCode()};JSON 反序列化时依次按 code 字符串、
 * {@link #getTitle()}、枚举名(忽略大小写)匹配枚举常量;Excel 导出写入 {@link #getTitle()}, Excel 导入按
 * {@link #getTitle()} 反查枚举常量(见各模块的 BaseEnum 序列化器/转换器)。
 * <p>
 * 需要把外部字符串还原成枚举常量时(数据库列值、canal 行数据、报表单元格等),统一用
 * {@link #resolve(Class, String)}:它与上面各处序列化器用的是同一条匹配规则,不要另写一份。
 * <p>
 * 实现示例:
 *
 * <pre>{@code
 * public enum HumidityType implements BaseEnum {
 *
 *     NORMAL(0, "正常");
 *
 *     private final int code;
 *     private final String title;
 *
 *     // 省略构造器与 getter
 * }
 * }</pre>
 */
public interface BaseEnum {

	/**
	 * 获取枚举编码, JSON 序列化时作为该枚举的输出值, JSON 反序列化时优先按该值匹配.
	 * @return 枚举编码
	 */
	int getCode();

	/**
	 * 获取枚举标题(展示文本), 用于 Excel 导出与界面展示, JSON 反序列化与 Excel 导入时也按该值匹配.
	 * @return 枚举标题
	 */
	String getTitle();

	/**
	 * 把字符串还原成枚举常量, 匹配顺序与各序列化器一致:先按 {@link #getCode()} 的字符串形式(区分大小写), 再忽略大小写地按
	 * {@link #getTitle()} 与枚举常量名匹配,返回第一个命中的常量.
	 * @param type 目标枚举类型, 必须实现本接口;不是枚举类时按"无匹配"处理
	 * @param value 待匹配的值, 首尾空白会被忽略
	 * @param <E> 目标枚举类型
	 * @return 匹配到的枚举常量;{@code value} 为 {@code null}、空串、{@code type} 不是枚举类或没有任何常量匹配时 返回
	 * {@code null}
	 */
	static <E extends BaseEnum> @Nullable E resolve(Class<E> type, @Nullable String value) {
		if (value == null) {
			return null;
		}

		String text = value.trim();
		if (text.isEmpty()) {
			return null;
		}

		E[] constants = type.getEnumConstants();
		if (constants == null) {
			return null;
		}

		for (E candidate : constants) {
			if (text.equals(String.valueOf(candidate.getCode())) || text.equalsIgnoreCase(candidate.getTitle())
					|| text.equalsIgnoreCase(((Enum<?>) candidate).name())) {
				return candidate;
			}
		}

		return null;
	}

}
