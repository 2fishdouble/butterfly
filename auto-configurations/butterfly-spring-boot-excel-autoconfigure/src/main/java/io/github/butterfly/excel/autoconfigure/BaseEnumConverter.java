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

/**
 * FastExcel 转换器:在实现 {@link BaseEnum} 的枚举与单元格标题文本之间互转.
 * <p>
 * 导出时取枚举的 {@code getTitle()} 作为单元格文本;导入时按当前字段声明的枚举类型遍历其枚举 常量,匹配 {@code getTitle()}
 * 相同的常量,匹配不到则抛异常。需随读/写构造器显式注册后生效。
 */
public class BaseEnumConverter implements Converter<BaseEnum> {

	/**
	 * 声明该转换器支持的 Java 类型.
	 * @return {@link BaseEnum},即所有实现该接口的枚举类型
	 */
	@Override
	public Class<?> supportJavaTypeKey() {
		return BaseEnum.class;
	}

	/**
	 * 声明该转换器支持的 Excel 单元格类型.
	 * @return {@link CellDataTypeEnum#STRING},枚举以文本形式读写
	 */
	@Override
	public CellDataTypeEnum supportExcelTypeKey() {
		return CellDataTypeEnum.STRING;
	}

	/**
	 * 将枚举写出为 Excel 单元格文本,内容取自枚举的 {@code getTitle()}.
	 * @param value 待写出的枚举值,可为 {@code null}
	 * @param contentProperty 当前字段的 Excel 内容属性,本方法未使用
	 * @param globalConfiguration 全局配置,本方法未使用
	 * @return 承载 {@code getTitle()} 的单元格数据;{@code value} 为 {@code null} 时返回空单元格数据
	 */
	@Override
	public WriteCellData<?> convertToExcelData(@Nullable BaseEnum value, ExcelContentProperty contentProperty,
			GlobalConfiguration globalConfiguration) {
		if (value == null) {
			return new WriteCellData<>();
		}
		return new WriteCellData<>(value.getTitle());
	}

	/**
	 * 将 Excel 单元格文本还原为枚举常量:按字段声明类型取其全部枚举常量,返回 {@code getTitle()} 与单元格文本相等的那个.
	 * @param cellData 当前单元格数据,取其字符串值作为标题进行匹配
	 * @param contentProperty 当前字段的 Excel 内容属性,用于取得字段的枚举类型
	 * @param globalConfiguration 全局配置,本方法未使用
	 * @return 标题匹配的枚举常量
	 * @throws IllegalArgumentException 字段类型不是 {@link BaseEnum} 的实现,或没有任何枚举常量的
	 * {@code getTitle()} 与单元格文本相等时抛出
	 */
	@Override
	public BaseEnum convertToJavaData(ReadCellData<?> cellData, ExcelContentProperty contentProperty,
			GlobalConfiguration globalConfiguration) {
		String text = cellData.getStringValue();
		Class<?> clazz = contentProperty.getField().getType();
		if (clazz.isEnum() && BaseEnum.class.isAssignableFrom(clazz)) {
			for (Object e : clazz.getEnumConstants()) {
				BaseEnum titleEnum = (BaseEnum) e;
				if (titleEnum.getTitle().equals(text)) {
					return titleEnum;
				}
			}
		}
		throw new IllegalArgumentException("No matching enum: " + text);
	}

}
