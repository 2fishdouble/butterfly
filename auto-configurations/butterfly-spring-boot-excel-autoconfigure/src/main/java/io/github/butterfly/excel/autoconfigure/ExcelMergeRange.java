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

import org.apache.poi.ss.util.CellRangeAddress;

/**
 * 合并区域:行列号均从 0 开始,与 {@link CellRangeAddress} 的语义一致,结束行/列包含在区域内.
 * <p>
 * 例如 {@code new ExcelMergeRange(0, 0, 0, 3)} 表示把第 1 行的前 4 列合并为一个单元格。作为
 * {@link ExcelMergeWriteHandler} 的入参使用,构造时校验区域边界。
 *
 * @param firstRow 起始行号(含)
 * @param lastRow 结束行号(含)
 * @param firstColumn 起始列号(含)
 * @param lastColumn 结束列号(含)
 */
public record ExcelMergeRange(int firstRow, int lastRow, int firstColumn, int lastColumn) {

	/**
	 * 校验行列号:起始行列号不能为负,结束行列号不能小于对应的起始行列号.
	 * @throws IllegalArgumentException 行列号非法时抛出
	 */
	public ExcelMergeRange {
		if (firstRow < 0 || firstColumn < 0) {
			throw new IllegalArgumentException(
					"Row and column index must not be negative: firstRow=" + firstRow + ", firstColumn=" + firstColumn);
		}
		if (lastRow < firstRow || lastColumn < firstColumn) {
			throw new IllegalArgumentException("Merge range end must not be before its start: rows " + firstRow + "-"
					+ lastRow + ", columns " + firstColumn + "-" + lastColumn);
		}
	}

	/**
	 * 转换为 POI 的合并区域表示,供写处理器调用.
	 * @return 与该区域等价的 {@link CellRangeAddress}
	 */
	CellRangeAddress toCellRangeAddress() {
		return new CellRangeAddress(this.firstRow, this.lastRow, this.firstColumn, this.lastColumn);
	}

}
