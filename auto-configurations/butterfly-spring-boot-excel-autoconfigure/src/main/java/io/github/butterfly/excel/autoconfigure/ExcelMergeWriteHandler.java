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

import org.apache.fesod.sheet.write.handler.SheetWriteHandler;
import org.apache.fesod.sheet.write.handler.context.SheetWriteHandlerContext;
import org.apache.poi.ss.usermodel.Sheet;

import java.util.List;

/**
 * 单元格合并处理器:在整张工作表写完后,按给定的 {@link ExcelMergeRange} 逐个合并区域.
 * <p>
 * 合并发生在 {@code afterSheetDispose} 阶段,因此合并区域覆盖的是最终写入的单元格;区域之间不能重叠, 否则由 POI 抛出异常。Fesod
 * 对每个工作表各调用一次该回调,同一组区域会应用到每个写出的工作表。
 */
public class ExcelMergeWriteHandler implements SheetWriteHandler {

	private final List<ExcelMergeRange> ranges;

	/**
	 * 按给定区域合并单元格.
	 * @param ranges 合并区域,按传入顺序依次合并,可为空(不合并任何区域)
	 */
	public ExcelMergeWriteHandler(ExcelMergeRange... ranges) {
		this.ranges = List.of(ranges);
	}

	/**
	 * 把配置的所有区域加入当前工作表的合并区域列表.
	 * @param context 当前工作表的写出上下文,用于取得 POI 的 {@link Sheet}
	 */
	@Override
	public void afterSheetDispose(SheetWriteHandlerContext context) {
		Sheet sheet = context.getWriteSheetHolder().getSheet();
		for (ExcelMergeRange range : this.ranges) {
			sheet.addMergedRegion(range.toCellRangeAddress());
		}
	}

}
