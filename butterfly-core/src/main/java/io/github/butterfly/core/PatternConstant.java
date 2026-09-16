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
 * 日期时间格式常量, 取值均为可直接传给 {@link java.time.format.DateTimeFormatter#ofPattern(String)} 或
 * {@link java.text.SimpleDateFormat} 的格式串.
 * <p>
 * 框架默认的 JSON 日期时间序列化/反序列化使用 {@link #DATE_TIME_FORMAT}({@code LocalDateTime})、
 * {@link #DATE_FORMAT}({@code LocalDate})、{@link #TIME_FORMAT}({@code LocalTime}),
 * 其余常量供业务按需引用。
 */
public class PatternConstant {

	/**
	 * 完整的日期时间格式, 值为 {@code yyyy-MM-dd HH:mm:ss}, 例如 {@code 2024-01-02 03:04:05}.
	 */
	public static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

	/**
	 * 不含秒的日期时间格式, 值为 {@code yyyy-MM-dd HH:mm}, 例如 {@code 2024-01-02 03:04}.
	 */
	public static final String DATE_TIME_FORMAT_NO_SECOND = "yyyy-MM-dd HH:mm";

	/**
	 * 日期格式, 值为 {@code yyyy-MM-dd}, 例如 {@code 2024-01-02}.
	 */
	public static final String DATE_FORMAT = "yyyy-MM-dd";

	/**
	 * 时间格式, 值为 {@code HH:mm:ss}, 例如 {@code 03:04:05}.
	 */
	public static final String TIME_FORMAT = "HH:mm:ss";

	/**
	 * 时:分格式, 值为 {@code HH:mm}, 例如 {@code 03:04}.
	 */
	public static final String HOUR_MINUTE_FORMAT = "HH:mm";

	/**
	 * 连续无分隔符的日期时间格式, 值为 {@code yyyyMMddHHmmss}, 例如 {@code 20240102030405},
	 * 常用于文件名、流水号等需要紧凑时间串的场景.
	 */
	public static final String CONTINUOUS_DATE_TIME_FORMAT = "yyyyMMddHHmmss";

}
