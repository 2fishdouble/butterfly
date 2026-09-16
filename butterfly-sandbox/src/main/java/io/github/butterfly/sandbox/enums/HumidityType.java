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

package io.github.butterfly.sandbox.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import io.github.butterfly.core.BaseEnum;
import lombok.Getter;

/**
 * 湿度类型枚举.
 * <p>
 * 描述环境湿度的档位划分,供业务侧按档位展示湿度区间说明.
 */
@Getter
public enum HumidityType implements BaseEnum {

	/**
	 * 正常.
	 */
	NORMAL(0, "正常（40%-60%）"),

	/**
	 * 干燥.
	 */
	DRY(1, "干燥（<=40%）");

	HumidityType(int code, String title) {
		this.code = code;
		this.title = title;
	}

	@EnumValue
	private final int code;

	private final String title;

	@Override
	public String toString() {
		return String.format("%s:%s", this.code, this.title);
	}

}
