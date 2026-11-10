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

import org.apache.fesod.sheet.metadata.data.WriteCellData;
import org.apache.fesod.sheet.write.handler.CellWriteHandler;
import org.apache.fesod.sheet.write.handler.context.CellWriteHandlerContext;
import org.apache.fesod.sheet.write.metadata.style.WriteCellStyle;
import org.apache.fesod.sheet.write.metadata.style.WriteFont;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.VerticalAlignment;

/**
 * 单元格样式处理器:表头行与数据行分别套用一组 {@link WriteCellStyle}.
 * <p>
 * 无参构造使用 {@link #defaultHeaderStyle()} 与 {@link #defaultContentStyle()}:表头为深蓝底、白色加粗
 * 字体、水平垂直居中,数据行为细边框、垂直居中;也可以自行构造 {@link WriteCellStyle} 传入。样式在 {@code afterCellDispose}
 * 阶段通过 {@link WriteCellData#setWriteCellStyle(WriteCellStyle)} 交给 Fesod 写入,因此对模板写出同样生效。
 */
public class ExcelStyleWriteHandler implements CellWriteHandler {

	private final WriteCellStyle headerStyle;

	private final WriteCellStyle contentStyle;

	/**
	 * 使用默认的表头样式与数据样式.
	 */
	public ExcelStyleWriteHandler() {
		this(defaultHeaderStyle(), defaultContentStyle());
	}

	/**
	 * 使用指定的表头样式与数据样式.
	 * @param headerStyle 表头单元格样式
	 * @param contentStyle 数据单元格样式
	 */
	public ExcelStyleWriteHandler(WriteCellStyle headerStyle, WriteCellStyle contentStyle) {
		this.headerStyle = headerStyle;
		this.contentStyle = contentStyle;
	}

	/**
	 * 构造默认表头样式:深蓝填充、白色加粗字体、水平垂直居中、四边细边框.
	 * @return 新的表头样式实例
	 */
	public static WriteCellStyle defaultHeaderStyle() {
		WriteCellStyle style = new WriteCellStyle();
		style.setFillPatternType(FillPatternType.SOLID_FOREGROUND);
		style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
		style.setHorizontalAlignment(HorizontalAlignment.CENTER);
		style.setVerticalAlignment(VerticalAlignment.CENTER);
		style.setBorderTop(BorderStyle.THIN);
		style.setBorderRight(BorderStyle.THIN);
		style.setBorderBottom(BorderStyle.THIN);
		style.setBorderLeft(BorderStyle.THIN);
		WriteFont font = new WriteFont();
		font.setBold(Boolean.TRUE);
		font.setFontHeightInPoints((short) 12);
		font.setColor(IndexedColors.WHITE.getIndex());
		style.setWriteFont(font);
		return style;
	}

	/**
	 * 构造默认数据样式:四边细边框、垂直居中,不改变数据本身的水平对齐方式.
	 * @return 新的数据样式实例
	 */
	public static WriteCellStyle defaultContentStyle() {
		WriteCellStyle style = new WriteCellStyle();
		style.setVerticalAlignment(VerticalAlignment.CENTER);
		style.setBorderTop(BorderStyle.THIN);
		style.setBorderRight(BorderStyle.THIN);
		style.setBorderBottom(BorderStyle.THIN);
		style.setBorderLeft(BorderStyle.THIN);
		return style;
	}

	/**
	 * 为每个已写出的单元格套用表头样式或数据样式.
	 * @param context 当前单元格的写出上下文,用于区分表头行与数据行并取出单元格数据
	 */
	@Override
	public void afterCellDispose(CellWriteHandlerContext context) {
		WriteCellData<?> cellData = context.getFirstCellData();
		if (cellData == null) {
			return;
		}
		cellData.setWriteCellStyle(Boolean.TRUE.equals(context.getHead()) ? this.headerStyle : this.contentStyle);
	}

}
