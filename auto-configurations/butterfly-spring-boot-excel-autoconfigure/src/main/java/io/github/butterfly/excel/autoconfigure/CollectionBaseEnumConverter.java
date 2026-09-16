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

package io.github.butterfly.excel.autoconfigure;

import cn.idev.excel.converters.Converter;
import cn.idev.excel.enums.CellDataTypeEnum;
import cn.idev.excel.metadata.GlobalConfiguration;
import cn.idev.excel.metadata.data.ReadCellData;
import cn.idev.excel.metadata.data.WriteCellData;
import cn.idev.excel.metadata.property.ExcelContentProperty;
import io.github.butterfly.core.BaseEnum;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * FastExcel 转换器:在元素为实现 {@link BaseEnum} 的 {@link Collection} 与逗号分隔的标题文本 之间互转.
 * <p>
 * 导出时把各元素的 {@code getTitle()} 用逗号拼接;导入时按逗号切分并逐个匹配标题。字段需声明为 带具体枚举类型参数的集合(如
 * {@code List<XxxEnum>}),以便从字段泛型中解析出元素枚举类型。
 */
public class CollectionBaseEnumConverter implements Converter<Collection<? extends BaseEnum>> {

	/**
	 * 声明该转换器支持的 Java 类型.
	 * @return {@link Collection},即元素为实现 {@link BaseEnum} 的枚举的集合类型
	 */
	@Override
	@SuppressWarnings("unchecked")
	public Class<Collection<? extends BaseEnum>> supportJavaTypeKey() {
		return (Class) Collection.class;
	}

	/**
	 * 声明该转换器支持的 Excel 单元格类型.
	 * @return {@link CellDataTypeEnum#STRING},集合以逗号分隔的文本读写
	 */
	@Override
	public CellDataTypeEnum supportExcelTypeKey() {
		return CellDataTypeEnum.STRING;
	}

	/**
	 * 将枚举集合写出为逗号分隔的标题文本,元素顺序即集合迭代顺序.
	 * @param value 待写出的枚举集合,可为 {@code null}
	 * @param contentProperty 当前字段的 Excel 内容属性,本方法未使用
	 * @param globalConfiguration 全局配置,本方法未使用
	 * @return 承载 {@code getTitle()} 拼接结果的单元格数据;{@code value} 为 {@code null} 或空集合时 返回空串
	 */
	@Override
	public WriteCellData<?> convertToExcelData(@Nullable Collection<? extends BaseEnum> value,
			ExcelContentProperty contentProperty, GlobalConfiguration globalConfiguration) {
		if (value == null || value.isEmpty()) {
			return new WriteCellData<>("");
		}
		String combinedTitle = value.stream().map(BaseEnum::getTitle).collect(Collectors.joining(","));
		return new WriteCellData<>(combinedTitle);
	}

	/**
	 * 将逗号分隔的标题文本还原为枚举集合:每个片段去除首尾空白后按 {@code getTitle()} 匹配.
	 * @param cellData 当前单元格数据,取其字符串值后按 {@code ","} 切分
	 * @param contentProperty 当前字段的 Excel 内容属性,用于从字段泛型中解析元素枚举类型
	 * @param globalConfiguration 全局配置,本方法未使用
	 * @return 匹配到的枚举列表,顺序与文本片段一致;文本为 {@code null} 或空白时返回空集合
	 * @throws IllegalArgumentException 元素枚举类型没有任何常量,或某个片段匹配不到任何枚举时抛出
	 */
	@Override
	public Collection<? extends BaseEnum> convertToJavaData(ReadCellData<?> cellData,
			ExcelContentProperty contentProperty, GlobalConfiguration globalConfiguration) {
		String cellString = cellData.getStringValue();
		if (cellString == null || cellString.trim().isEmpty()) {
			return new ArrayList<>();
		}

		ParameterizedType parameterizedType = (ParameterizedType) contentProperty.getField().getGenericType();
		@SuppressWarnings("unchecked")
		Class<BaseEnum> enumClass = (Class<BaseEnum>) parameterizedType.getActualTypeArguments()[0];

		BaseEnum[] allEnums = enumClass.getEnumConstants();
		if (allEnums == null || allEnums.length == 0) {
			throw new IllegalArgumentException("Can not find enum constants for " + enumClass.getName() + ".");
		}

		return Stream.of(cellString.split(","))
			.map(String::trim)
			.map((desc) -> findEnumByDesc(allEnums, desc))
			.toList();
	}

	private BaseEnum findEnumByDesc(BaseEnum[] enums, String desc) {
		for (BaseEnum e : enums) {
			if (e.getTitle().equals(desc)) {
				return e;
			}
		}
		throw new IllegalArgumentException("Can not convert description '" + desc + "' to enum type.");
	}

}
