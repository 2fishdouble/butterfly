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

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.core.convert.ConversionService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 默认的行数据映射器:用 Spring 的 {@link org.springframework.beans.PropertyAccessor} 把列值绑定到实体属性上.
 * <p>
 * 转换规则:
 * <ul>
 * <li>列名按下划线转驼峰后匹配属性,因此 MySQL 的 {@code create_time} 会绑定到 {@code createTime};</li>
 * <li>实体上没有对应属性的列被忽略({@code ignoreUnknown}),因为 canal 会带上整行所有列,而实体通常只关心其中 一部分;</li>
 * <li>属性类型转换交给 {@link ApplicationConversionService}(Boot 绑定配置属性用的同一套转换器),因此字符串到
 * 数字、枚举、布尔、日期时间都能直接转换;日期时间额外兼容 MySQL 常见的空格分隔写法 {@code yyyy-MM-dd HH:mm:ss[.SSS]},而不只是 ISO
 * 的 {@code T} 分隔写法;</li>
 * <li>实体必须有默认构造方法与 setter,不支持不可变类型与 record;</li>
 * <li>列本身为 {@code NULL} 时不会进入映射(见 {@link CanalEvent} 的说明),属性保持默认值;</li>
 * <li>类型转换失败会抛出 Spring 的绑定异常,由消费端按处理失败回滚,不会被静默忽略。</li>
 * </ul>
 * <p>
 * 需要映射 JSON 列、嵌套对象或 record 时,自行实现 {@link CanalRowMapper} 并注册为 Bean。
 */
public class BeanWrapperCanalRowMapper implements CanalRowMapper {

	/**
	 * MySQL 导出的日期时间常见为 {@code 2024-01-01 10:00:00} 或带小数秒的同款写法,这里在 ISO 解析失败后用它兜底.
	 */
	private static final DateTimeFormatter SPACE_SEPARATED_DATE_TIME = new DateTimeFormatterBuilder()
		.appendPattern("yyyy-MM-dd HH:mm:ss")
		.optionalStart()
		.appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
		.optionalEnd()
		.toFormatter();

	private final ConversionService conversionService;

	/**
	 * 用默认转换器创建映射器.
	 */
	public BeanWrapperCanalRowMapper() {
		this(createDefaultConversionService());
	}

	/**
	 * 用指定转换器创建映射器.
	 * @param conversionService 属性类型转换器,不能为空
	 */
	public BeanWrapperCanalRowMapper(ConversionService conversionService) {
		this.conversionService = conversionService;
	}

	/**
	 * 把一行数据绑定到目标类型的新实例上.
	 * @param row 列名到列值的映射
	 * @param type 目标实体类型,需要有默认构造方法与 setter
	 * @param <T> 目标实体类型
	 * @return 映射后的实体对象
	 * @throws IllegalStateException 目标类型没有默认构造方法时抛出
	 * @throws org.springframework.beans.BeansException 类型转换失败或属性不可写时抛出
	 */
	@Override
	public <T> T map(Map<String, String> row, Class<T> type) {
		T bean = instantiate(type);
		BeanWrapper accessor = PropertyAccessorFactory.forBeanPropertyAccess(bean);
		accessor.setConversionService(this.conversionService);
		accessor.setPropertyValues(new MutablePropertyValues(camelCaseNames(row)), true);
		return bean;
	}

	private static ConversionService createDefaultConversionService() {
		ApplicationConversionService conversionService = new ApplicationConversionService();
		conversionService.addConverter(String.class, LocalDateTime.class,
				BeanWrapperCanalRowMapper::parseLocalDateTime);
		return conversionService;
	}

	private static LocalDateTime parseLocalDateTime(String value) {
		String text = value.trim();
		try {
			return LocalDateTime.parse(text);
		}
		catch (DateTimeParseException ex) {
			return LocalDateTime.parse(text, SPACE_SEPARATED_DATE_TIME);
		}
	}

	private static <T> T instantiate(Class<T> type) {
		try {
			return type.getDeclaredConstructor().newInstance();
		}
		catch (ReflectiveOperationException ex) {
			throw new IllegalStateException("Cannot instantiate canal row type " + type.getName()
					+ "; it must declare a default constructor and setters", ex);
		}
	}

	private static Map<String, String> camelCaseNames(Map<String, String> row) {
		Map<String, String> renamed = new LinkedHashMap<>(row.size());
		row.forEach((name, value) -> renamed.put(toCamelCase(name), value));
		return renamed;
	}

	private static String toCamelCase(String column) {
		if (column.indexOf('_') < 0) {
			return column;
		}

		StringBuilder result = new StringBuilder(column.length());
		boolean upperCaseNext = false;
		for (int i = 0; i < column.length(); i++) {
			char character = column.charAt(i);
			if (character == '_') {
				upperCaseNext = true;
				continue;
			}
			result.append(upperCaseNext ? Character.toUpperCase(character) : Character.toLowerCase(character));
			upperCaseNext = false;
		}
		return result.toString();
	}

}
