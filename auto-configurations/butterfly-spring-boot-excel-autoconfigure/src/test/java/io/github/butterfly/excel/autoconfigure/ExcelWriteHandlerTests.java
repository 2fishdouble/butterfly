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

import org.apache.fesod.sheet.write.metadata.style.WriteCellStyle;
import org.apache.fesod.sheet.write.metadata.style.WriteFont;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

/**
 * 覆盖样式、行高与单元格合并三个写处理器的实际写出效果,并把生成的 xlsx 落盘到项目根目录的 {@code test-files} 供人工打开核对.
 */
class ExcelWriteHandlerTests {

	private static final Path TEST_FILES = testFilesDirectory();

	private final List<SampleRow> rows = List.of(SampleRow.of("butterfly", 3), SampleRow.of("dsh", 7));

	private final List<SampleRow> repeatedNames = List.of(SampleRow.of("butterfly", 3), SampleRow.of("butterfly", 7));

	@Test
	void styleHandlerAppliesHeaderAndContentStyles() throws IOException {
		byte[] bytes = ExcelUtil.write(this.rows, SampleRow.class, new ExcelStyleWriteHandler());
		save("ExcelStyleWriteHandler.xlsx", bytes);

		try (Workbook workbook = open(bytes)) {
			Sheet sheet = workbook.getSheetAt(0);

			CellStyle header = sheet.getRow(0).getCell(0).getCellStyle();
			Font headerFont = font(workbook, header);
			assertThat(headerFont.getBold()).isTrue();
			assertThat(headerFont.getColor()).isEqualTo(IndexedColors.WHITE.getIndex());
			assertThat(header.getFillPattern()).isEqualTo(FillPatternType.SOLID_FOREGROUND);
			assertThat(header.getFillForegroundColor()).isEqualTo(IndexedColors.DARK_BLUE.getIndex());
			assertThat(header.getAlignment()).isEqualTo(HorizontalAlignment.CENTER);
			assertThat(header.getVerticalAlignment()).isEqualTo(VerticalAlignment.CENTER);
			assertThat(header.getBorderBottom()).isEqualTo(BorderStyle.THIN);

			CellStyle content = sheet.getRow(1).getCell(0).getCellStyle();
			assertThat(font(workbook, content).getBold()).isFalse();
			assertThat(content.getVerticalAlignment()).isEqualTo(VerticalAlignment.CENTER);
			assertThat(content.getBorderTop()).isEqualTo(BorderStyle.THIN);
			assertThat(content.getBorderLeft()).isEqualTo(BorderStyle.THIN);
		}
	}

	@Test
	void styleHandlerAcceptsCustomStyles() throws IOException {
		WriteCellStyle customHeader = new WriteCellStyle();
		WriteFont customFont = new WriteFont();
		customFont.setBold(Boolean.TRUE);
		customFont.setColor(IndexedColors.RED.getIndex());
		customHeader.setWriteFont(customFont);
		byte[] bytes = ExcelUtil.write(this.rows, SampleRow.class,
				new ExcelStyleWriteHandler(customHeader, ExcelStyleWriteHandler.defaultContentStyle()));
		save("ExcelStyleWriteHandler-custom.xlsx", bytes);

		try (Workbook workbook = open(bytes)) {
			Sheet sheet = workbook.getSheetAt(0);
			CellStyle header = sheet.getRow(0).getCell(0).getCellStyle();
			assertThat(font(workbook, header).getColor()).isEqualTo(IndexedColors.RED.getIndex());
			assertThat(header.getFillPattern()).isEqualTo(FillPatternType.NO_FILL);
			assertThat(font(workbook, sheet.getRow(1).getCell(0).getCellStyle()).getBold()).isFalse();
		}
	}

	@Test
	void rowHeightHandlerAppliesHeaderAndContentHeights() throws IOException {
		byte[] bytes = ExcelUtil.write(this.rows, SampleRow.class, new ExcelRowHeightWriteHandler(36f, 24f));
		save("ExcelRowHeightWriteHandler.xlsx", bytes);

		try (Workbook workbook = open(bytes)) {
			Sheet sheet = workbook.getSheetAt(0);
			assertThat(sheet.getRow(0).getHeightInPoints()).isCloseTo(36f, within(0.5f));
			assertThat(sheet.getRow(1).getHeightInPoints()).isCloseTo(24f, within(0.5f));
			assertThat(sheet.getRow(2).getHeightInPoints()).isCloseTo(24f, within(0.5f));
		}
	}

	@Test
	void mergeHandlerMergesConfiguredRanges() throws IOException {
		byte[] bytes = ExcelUtil.write(this.repeatedNames, SampleRow.class,
				new ExcelMergeWriteHandler(new ExcelMergeRange(1, 2, 0, 0)));
		save("ExcelMergeWriteHandler.xlsx", bytes);

		try (Workbook workbook = open(bytes)) {
			Sheet sheet = workbook.getSheetAt(0);
			assertThat(sheet.getNumMergedRegions()).isEqualTo(1);

			CellRangeAddress merged = sheet.getMergedRegion(0);
			assertThat(merged.getFirstRow()).isEqualTo(1);
			assertThat(merged.getLastRow()).isEqualTo(2);
			assertThat(merged.getFirstColumn()).isZero();
			assertThat(merged.getLastColumn()).isZero();
			assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("butterfly");
		}
	}

	@Test
	void handlersCombineIntoOneWorkbook() throws IOException {
		byte[] bytes = ExcelUtil.write(this.repeatedNames, SampleRow.class, new ExcelStyleWriteHandler(),
				new ExcelRowHeightWriteHandler(), new ExcelMergeWriteHandler(new ExcelMergeRange(1, 2, 0, 0)));
		Path file = save("ExcelWriteHandlers-demo.xlsx", bytes);

		assertThat(Files.size(file)).isPositive();

		try (Workbook workbook = open(bytes)) {
			Sheet sheet = workbook.getSheetAt(0);
			assertThat(font(workbook, sheet.getRow(0).getCell(0).getCellStyle()).getBold()).isTrue();
			assertThat(sheet.getRow(0).getHeightInPoints()).isCloseTo(30f, within(0.5f));
			assertThat(sheet.getRow(1).getHeightInPoints()).isCloseTo(20f, within(0.5f));
			assertThat(sheet.getNumMergedRegions()).isEqualTo(1);
		}
	}

	@Test
	void handlersRejectInvalidConfiguration() {
		assertThatIllegalArgumentException().isThrownBy(() -> new ExcelMergeRange(-1, 0, 0, 0));
		assertThatIllegalArgumentException().isThrownBy(() -> new ExcelMergeRange(2, 1, 0, 0));
		assertThatIllegalArgumentException().isThrownBy(() -> new ExcelMergeRange(0, 0, 3, 2));
		assertThatIllegalArgumentException().isThrownBy(() -> new ExcelRowHeightWriteHandler(0f, 20f));
		assertThatIllegalArgumentException().isThrownBy(() -> new ExcelRowHeightWriteHandler(30f, -1f));
	}

	private static Path save(String fileName, byte[] bytes) throws IOException {
		Path file = TEST_FILES.resolve(fileName);
		Files.write(file, bytes);
		return file;
	}

	private static Font font(Workbook workbook, CellStyle style) {
		return workbook.getFontAt(style.getFontIndex());
	}

	private static Workbook open(byte[] bytes) throws IOException {
		return new XSSFWorkbook(new ByteArrayInputStream(bytes));
	}

	/**
	 * 从当前工作目录向上找到包含 {@code mvnw} 的项目根目录,再定位(必要时创建)根目录下的 {@code test-files}.
	 * @return 项目根目录下的 test-files 路径
	 */
	private static Path testFilesDirectory() {
		Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
		for (int depth = 0; depth < 5 && isNotProjectRoot(root) && root.getParent() != null; depth++) {
			root = root.getParent();
		}
		try {
			return Files.createDirectories(root.resolve("test-files"));
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static boolean isNotProjectRoot(Path directory) {
		return !Files.isRegularFile(directory.resolve("mvnw")) && !Files.isRegularFile(directory.resolve("mvnw.cmd"));
	}

}
