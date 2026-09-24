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

import io.github.butterfly.core.BaseEnum;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
 * <li>实现 {@link BaseEnum} 的枚举属性按 code、title、枚举名的顺序匹配(与 JSON 反序列化同一条规则),因此库里存的是 code
 * 也能映射过来;属性是集合或数组时({@code List<WeekType>}、{@code WeekType[]}),列值按 {@code [0,1]} 或
 * {@code 0,1} 逐项匹配,因此 {@code [0,6]} 能映射成 {@code [MONDAY, SUNDAY]};未实现 {@link BaseEnum}
 * 的普通枚举仍只认枚举名;</li>
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
		accessor.setPropertyValues(new MutablePropertyValues(resolveBaseEnums(camelCaseNames(row), accessor)), true);
		return bean;
	}

	/**
	 * 把 {@link BaseEnum} 属性的列值先还原成枚举名.
	 * <p>
	 * canal 给出的列值都是字符串,而 Spring 的字符串转枚举只认枚举名;本项目的枚举约定是 {@link BaseEnum}(库里存的是
	 * {@link BaseEnum#getCode()}),所以这里先按 {@link BaseEnum#resolve} 匹配出常量,再把枚举名交给转换器,
	 * 这样每种枚举都不需要单独写一份转换器。
	 * <p>
	 * 集合与数组({@code List<WeekType>}、{@code WeekType[]})的列值支持两种写法:{@code [0,1]}(JSON
	 * 数组,项目默认写法, 元素写成 {@code ["MONDAY","SUNDAY"]} 这样的字符串也可以)与 {@code 0,1}(MySQL 的
	 * {@code SET} 列、Spring 自己的字符串转集合都是这个形式)。这里统一摊平成逗号分隔并逐项换名,剩下的切分与元素转换继续交给 Spring。
	 * 匹配不到时保留原值,由转换器按原有方式报错。
	 * @param row 已转成驼峰的列名到列值映射
	 * @param accessor 目标实体的属性访问器,用于取属性类型
	 * @return 可交给 Spring 绑定的列值映射
	 */
	private static Map<String, String> resolveBaseEnums(Map<String, String> row, BeanWrapper accessor) {
		Map<String, String> resolved = new LinkedHashMap<>(row.size());
		row.forEach((name, value) -> {
			TypeDescriptor propertyType = accessor.getPropertyTypeDescriptor(name);
			resolved.put(name, (propertyType != null) ? resolveBaseEnumNames(propertyType, value) : value);
		});
		return resolved;
	}

	/**
	 * 把一个列值里的 {@link BaseEnum} code 换成枚举名;集合与数组还会把 JSON 数组写法摊平成逗号分隔.
	 * @param propertyType 目标属性的类型描述
	 * @param value 列值
	 * @return 可交给 Spring 转换的值
	 */
	private static String resolveBaseEnumNames(TypeDescriptor propertyType, String value) {
		if (!propertyType.isArray() && !propertyType.isCollection()) {
			Class<?> propertyClass = propertyType.getType();
			return (BaseEnum.class.isAssignableFrom(propertyClass)) ? resolveBaseEnumName(propertyClass, value) : value;
		}

		Class<?> elementType = baseEnumElementType(propertyType);
		return elementsOf(value).stream()
			.map((element) -> (elementType != null) ? resolveBaseEnumName(elementType, element) : element)
			.collect(Collectors.joining(","));
	}

	/**
	 * 把单个值还原成枚举名;匹配不到时原样返回,由转换器按原有方式报错.
	 * @param enumType 枚举类型
	 * @param value 单个值
	 * @return 枚举名,或匹配不到时的原值
	 */
	private static String resolveBaseEnumName(Class<?> enumType, String value) {
		BaseEnum resolved = resolveBaseEnum(enumType, value);
		return (resolved != null) ? ((Enum<?>) resolved).name() : value;
	}

	/**
	 * 把一个列表列值切成元素:去掉 JSON 数组的方括号与元素两侧的引号,再按逗号切分.
	 * @param value 列值,形如 {@code [0,1]}、{@code ["MONDAY","SUNDAY"]} 或 {@code 0,1}
	 * @return 元素列表;空值或 {@code []} 返回空列表
	 */
	private static List<String> elementsOf(String value) {
		String text = value.trim();
		if (text.startsWith("[") && text.endsWith("]")) {
			text = text.substring(1, text.length() - 1);
		}
		if (text.isBlank()) {
			return List.of();
		}

		return Arrays.stream(StringUtils.commaDelimitedListToStringArray(text))
			.map(BeanWrapperCanalRowMapper::unquote)
			.toList();
	}

	/**
	 * 去掉 JSON 字符串元素两侧的引号.
	 * @param element 元素原文
	 * @return 去掉引号与首尾空白后的元素
	 */
	private static String unquote(String element) {
		String text = element.trim();
		boolean quoted = (text.length() >= 2) && (text.charAt(0) == text.charAt(text.length() - 1))
				&& (text.charAt(0) == '"' || text.charAt(0) == '\'');
		return (quoted) ? text.substring(1, text.length() - 1) : text;
	}

	/**
	 * 取属性上承载 {@link BaseEnum} 的类型:属性是集合或数组时取元素类型,属性本身是枚举时取它,其余返回 {@code null}.
	 * @param propertyType 目标属性的类型描述
	 * @return 承载 BaseEnum 的类型,或 {@code null}
	 */
	private static @Nullable Class<?> baseEnumElementType(TypeDescriptor propertyType) {
		if (propertyType.isArray() || propertyType.isCollection()) {
			TypeDescriptor elementType = propertyType.getElementTypeDescriptor();
			Class<?> elementClass = (elementType != null) ? elementType.getType() : null;
			return (elementClass != null && BaseEnum.class.isAssignableFrom(elementClass)) ? elementClass : null;
		}

		Class<?> propertyClass = propertyType.getType();
		return (BaseEnum.class.isAssignableFrom(propertyClass)) ? propertyClass : null;
	}

	@SuppressWarnings("unchecked")
	private static @Nullable BaseEnum resolveBaseEnum(Class<?> enumType, String value) {
		return BaseEnum.resolve((Class<? extends BaseEnum>) enumType, value);
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
