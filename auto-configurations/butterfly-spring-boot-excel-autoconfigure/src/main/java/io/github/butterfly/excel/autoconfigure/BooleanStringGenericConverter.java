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
import org.jspecify.annotations.Nullable;

/**
 * FastExcel 转换器:在 {@link Boolean} 与 Excel 单元格文本之间互转,并支持字段级自定义文案.
 * <p>
 * 字段标注 {@link BooleanMapping} 时按其 {@code trueValue} / {@code falseValue} 双向映射; 未标注(或字段为
 * {@code null})时退回 {@code Boolean.toString()} 与 {@code Boolean.valueOf(String)} 的默认规则。
 */
public class BooleanStringGenericConverter implements Converter<Boolean> {

	/**
	 * 声明该转换器支持的 Java 类型.
	 * @return {@link Boolean},即包装类型
	 */
	@Override
	public Class<?> supportJavaTypeKey() {
		return Boolean.class;
	}

	/**
	 * 声明该转换器支持的 Excel 单元格类型.
	 * @return {@link CellDataTypeEnum#STRING},布尔以文本形式读写
	 */
	@Override
	public CellDataTypeEnum supportExcelTypeKey() {
		return CellDataTypeEnum.STRING;
	}

	/**
	 * 将单元格文本解析为布尔值.
	 * <p>
	 * 字段带 {@link BooleanMapping} 时先按 {@code trueValue} / {@code falseValue} 精确匹配;
	 * 都未命中(或没有该注解)时按 {@code Boolean.valueOf(String)} 解析,即忽略大小写的 {@code "true"} 得到
	 * {@code true},其余文本(含数字与空串)均得到 {@code false}。
	 * @param cellData 当前单元格数据,取其字符串值进行匹配
	 * @param contentProperty 当前字段的 Excel 内容属性,用于读取字段上的 {@link BooleanMapping}
	 * @param globalConfiguration 全局配置,本方法未使用
	 * @return 解析得到的布尔值,不会为 {@code null}
	 */
	@Override
	public Boolean convertToJavaData(ReadCellData<?> cellData, ExcelContentProperty contentProperty,
			GlobalConfiguration globalConfiguration) {
		String str = cellData.getStringValue();
		BooleanMapping mapping = getMapping(contentProperty);
		if (mapping != null) {
			if (mapping.trueValue().equals(str)) {
				return true;
			}
			if (mapping.falseValue().equals(str)) {
				return false;
			}
		}
		return Boolean.valueOf(str);
	}

	/**
	 * 将布尔值写出为 Excel 单元格文本.
	 * <p>
	 * 字段带 {@link BooleanMapping} 时取注解中的文案,否则取 {@code Boolean.toString(value)} (即
	 * {@code "true"} / {@code "false"})。实现未对 {@code value} 为 {@code null} 的情况 做兼容。
	 * @param value 待写出的布尔值
	 * @param contentProperty 当前字段的 Excel 内容属性,用于读取字段上的 {@link BooleanMapping}
	 * @param globalConfiguration 全局配置,本方法未使用
	 * @return 承载文本的单元格数据
	 */
	@Override
	public WriteCellData<?> convertToExcelData(Boolean value, ExcelContentProperty contentProperty,
			GlobalConfiguration globalConfiguration) {
		BooleanMapping mapping = getMapping(contentProperty);
		String stringValue;
		if (mapping != null) {
			stringValue = value ? mapping.trueValue() : mapping.falseValue();
		}
		else {
			stringValue = value.toString();
		}
		return new WriteCellData<>(stringValue);
	}

	private @Nullable BooleanMapping getMapping(ExcelContentProperty contentProperty) {
		if (contentProperty.getField() == null) {
			return null;
		}
		return contentProperty.getField().getAnnotation(BooleanMapping.class);
	}

}
