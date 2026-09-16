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
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ValueDeserializer;

/**
 * {@link BaseEnum} 枚举的 Jackson 反序列化器,把 JSON 中的 code、title 或枚举常量名还原为枚举实例.
 * <p>
 * 具体匹配规则见 {@link BaseEnumJsonFactory#parse}。本类可以无参创建(此时未绑定枚举类型, 任何输入都返回 {@code null}),也可由
 * {@link #createContextual(DeserializationContext, BeanProperty)} 按属性声明的枚举类型重新创建。
 */
public class BaseEnumDeserializer extends ValueDeserializer<BaseEnum> {

	private final @Nullable Class<? extends BaseEnum> enumType;

	/**
	 * 创建未绑定枚举类型的反序列化器,内部枚举类型为 {@code null}, 因此
	 * {@link #deserialize(JsonParser, DeserializationContext)} 对任何输入都返回 {@code null}.
	 * <p>
	 * 该构造器主要供 Jackson 先实例化、再由
	 * {@link #createContextual(DeserializationContext, BeanProperty)} 返回绑定具体枚举类型的副本;
	 * 直接使用时请改用 {@link #BaseEnumDeserializer(Class)}。
	 */
	public BaseEnumDeserializer() {
		this.enumType = null;
	}

	/**
	 * 创建绑定指定枚举类型的反序列化器.
	 * @param enumType 目标枚举类型,必须实现 {@link BaseEnum} 且本身是枚举类; 传入 {@code null}
	 * 会使反序列化阶段无法解析任何值
	 */
	public BaseEnumDeserializer(Class<? extends BaseEnum> enumType) {
		this.enumType = enumType;
	}

	/**
	 * 依据当前上下文的目标类型,创建绑定到具体枚举类型的反序列化器.
	 * <p>
	 * 目标类型存在且是 {@link BaseEnum} 的实现类时,返回绑定该枚举类型的新实例; 目标类型不可得或就是 {@link BaseEnum}
	 * 接口本身时,原样返回当前实例。
	 * @param ctxt 反序列化上下文,用于获取当前目标类型
	 * @param property 正在反序列化的 Bean 属性,可能为 {@code null}(例如根值或集合元素)
	 * @return 绑定具体枚举类型的新反序列化器,或当前实例
	 */
	@Override
	@SuppressWarnings("unchecked")
	public ValueDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
		JavaType type = ctxt.getContextualType();
		if (type != null && BaseEnum.class.isAssignableFrom(type.getRawClass())) {
			return new BaseEnumDeserializer((Class<? extends BaseEnum>) type.getRawClass());
		}
		return this;
	}

	/**
	 * 把当前 JSON 值按字符串读取,并解析为对应的 {@link BaseEnum} 枚举常量.
	 * <p>
	 * 未绑定枚举类型、值为 {@code null} 或去除首尾空白后为空时返回 {@code null}; 值无法匹配任何枚举常量时不向外抛出
	 * {@link IllegalArgumentException}, 而是转换成 Jackson 的字符串值异常。
	 * @param p 当前 JSON 解析器,值会被当作字符串读取
	 * @param ctxt 反序列化上下文,用于构造类型不匹配异常
	 * @return 解析出的枚举常量;输入为空或未绑定枚举类型时返回 {@code null}
	 * @throws JacksonException 读取 JSON 失败,或字符串值无法解析为目标枚举类型时抛出
	 */
	@Override
	public @Nullable BaseEnum deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
		if (this.enumType == null) {
			return null;
		}
		String v = p.getString();
		if (v == null || v.trim().isEmpty()) {
			return null;
		}

		try {
			return BaseEnumJsonFactory.parse(this.enumType, v.trim());
		}
		catch (IllegalArgumentException ex) {
			throw ctxt.weirdStringException(v, this.enumType, ex.getMessage());
		}
	}

}
