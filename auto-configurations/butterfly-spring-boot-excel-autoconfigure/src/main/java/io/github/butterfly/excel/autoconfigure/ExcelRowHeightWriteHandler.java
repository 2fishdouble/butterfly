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

import org.apache.fesod.sheet.write.handler.CellWriteHandler;
import org.apache.fesod.sheet.write.handler.context.CellWriteHandlerContext;
import org.apache.poi.ss.usermodel.Row;

/**
 * 行高处理器:表头行与数据行使用不同的固定行高.
 * <p>
 * 行高单位为磅(与 Excel 界面一致),在 {@code afterCellDispose} 阶段按行设置;同一行的多个单元格 重复设置同一高度,结果幂等。无参构造使用
 * 30 磅表头行与 20 磅数据行。
 */
public class ExcelRowHeightWriteHandler implements CellWriteHandler {

	private final float headerRowHeight;

	private final float contentRowHeight;

	/**
	 * 使用默认行高:表头 30 磅、数据行 20 磅.
	 */
	public ExcelRowHeightWriteHandler() {
		this(30f, 20f);
	}

	/**
	 * 使用指定的表头行高与数据行高.
	 * @param headerRowHeight 表头行高,单位为磅
	 * @param contentRowHeight 数据行高,单位为磅
	 * @throws IllegalArgumentException 任一高度不为正数时抛出
	 */
	public ExcelRowHeightWriteHandler(float headerRowHeight, float contentRowHeight) {
		if (headerRowHeight <= 0 || contentRowHeight <= 0) {
			throw new IllegalArgumentException("Row height must be positive: headerRowHeight=" + headerRowHeight
					+ ", contentRowHeight=" + contentRowHeight);
		}
		this.headerRowHeight = headerRowHeight;
		this.contentRowHeight = contentRowHeight;
	}

	/**
	 * 按当前行是表头行还是数据行设置行高.
	 * @param context 当前单元格的写出上下文,用于取得所在行并区分表头行与数据行
	 */
	@Override
	public void afterCellDispose(CellWriteHandlerContext context) {
		Row row = context.getRow();
		if (row == null) {
			return;
		}
		row.setHeightInPoints(Boolean.TRUE.equals(context.getHead()) ? this.headerRowHeight : this.contentRowHeight);
	}

}
