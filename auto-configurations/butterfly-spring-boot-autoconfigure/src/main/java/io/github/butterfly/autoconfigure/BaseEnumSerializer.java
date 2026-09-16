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
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * {@link BaseEnum} 枚举的 Jackson 序列化器:把枚举写为 {@link BaseEnum#getCode()} 返回的数值, 而不是默认的枚举常量名.
 * <p>
 * 值为 {@code null} 时写出 JSON {@code null}。
 */
public class BaseEnumSerializer extends ValueSerializer<BaseEnum> {

	/**
	 * 把枚举序列化为其 code 数值.
	 * @param value 待序列化的枚举,可为 {@code null}
	 * @param gen JSON 生成器
	 * @param serializers 序列化上下文,本实现未使用
	 */
	@Override
	public void serialize(@Nullable BaseEnum value, JsonGenerator gen, SerializationContext serializers) {
		if (value == null) {
			gen.writeNull();
		}
		else {
			gen.writeNumber(value.getCode());
		}
	}

}
