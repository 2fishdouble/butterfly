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

package io.github.butterfly.sandbox.model;

import io.github.butterfly.core.BaseEnum;
import lombok.Getter;

/**
 * 周类型:编码枚举,库中存 {@link #getCode()},展示与报表用 {@link #getTitle()}.
 */
@Getter
public enum WeekType implements BaseEnum {

	/**
	 * 周一.
	 */
	MONDAY(0, "周一"),
	/**
	 * 周二.
	 */
	TUESDAY(1, "周二"),
	/**
	 * 周三.
	 */
	WEDNESDAY(2, "周三"),
	/**
	 * 周四.
	 */
	THURSDAY(3, "周四"),
	/**
	 * 周五.
	 */
	FRIDAY(4, "周五"),
	/**
	 * 周六.
	 */
	SATURDAY(5, "周六"),
	/**
	 * 周日.
	 */
	SUNDAY(6, "周日");

	private final int code;

	private final String title;

	WeekType(int code, String title) {
		this.code = code;
		this.title = title;
	}

}
