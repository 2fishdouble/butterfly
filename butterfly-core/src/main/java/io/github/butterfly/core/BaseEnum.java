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

/**
 * 枚举统一接口, 实现本接口的枚举可被框架统一序列化与展示.
 * <p>
 * 约定行为:JSON 序列化时只输出 {@link #getCode()};JSON 反序列化时依次按 code 字符串、
 * {@link #getTitle()}、枚举名(忽略大小写)匹配枚举常量;Excel 导出写入 {@link #getTitle()}, Excel 导入按
 * {@link #getTitle()} 反查枚举常量(见各模块的 BaseEnum 序列化器/转换器)。
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

}
