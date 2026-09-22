package io.github.butterfly.excel.autoconfigure;

import cn.idev.excel.annotation.ExcelProperty;
import cn.idev.excel.converters.Converter;
import cn.idev.excel.converters.WriteConverterContext;
import cn.idev.excel.enums.CellDataTypeEnum;
import cn.idev.excel.metadata.GlobalConfiguration;
import cn.idev.excel.metadata.data.WriteCellData;
import cn.idev.excel.metadata.property.ExcelContentProperty;
import cn.idev.excel.write.handler.CellWriteHandler;
import cn.idev.excel.write.handler.context.CellWriteHandlerContext;
import io.github.butterfly.core.BusinessException;
import jakarta.servlet.ServletOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 覆盖 Excel 读写工具类的往返读写、下载响应与失败包装.
 */
class ExcelUtilTests {

	private static final String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

	private final List<SampleRow> rows = List.of(SampleRow.of("butterfly", 3), SampleRow.of("dsh", 7));

	@Test
	void writeThenReadBackKeepsEveryConvertedField() throws IOException {
		byte[] bytes = ExcelUtil.write("sample", this.rows, SampleRow.class);

		assertThat(bytes).isNotEmpty();

		List<SampleRow> read = readAll(bytes, 1);

		assertThat(read).hasSize(2);
		SampleRow first = read.getFirst();
		assertThat(first.getName()).isEqualTo("butterfly");
		assertThat(first.getAge()).isEqualTo(3);
		assertThat(first.getType()).isEqualTo(SampleEnum.FIRST);
		assertThat(first.getTypes()).containsExactly(SampleEnum.FIRST, SampleEnum.SECOND);
		// 空集合写出为空单元格,读回时 FastExcel 不会调用转换器,字段保持 null
		assertThat(first.getEmptyTypes()).isNullOrEmpty();
		assertThat(first.getEnabled()).isTrue();
		assertThat(first.getPlain()).isFalse();
		assertThat(read.get(1).getName()).isEqualTo("dsh");
	}

	@Test
	void readFromByteArrayInvokesVerifyCallbackForEveryRow() throws IOException {
		byte[] bytes = ExcelUtil.write("sample", this.rows, SampleRow.class);
		AtomicInteger verified = new AtomicInteger();

		ExcelUtil.read(bytes, SampleRow.class, (row, context) -> verified.incrementAndGet(), (list) -> {
		}, 1, "sheet1");

		assertThat(verified).hasValue(2);
	}

	@Test
	void readFromMultipartFile() throws IOException {
		MockMultipartFile file = multipartFile();

		List<SampleRow> read = new ArrayList<>();
		ExcelUtil.read(file, SampleRow.class, (row, context) -> {
		}, read::addAll);

		assertThat(read).hasSize(2);
		assertThat(read.getFirst().getName()).isEqualTo("butterfly");
	}

	@Test
	void readFromMultipartFileWithHeadRowNumber() throws IOException {
		MockMultipartFile file = multipartFile();

		List<SampleRow> read = new ArrayList<>();
		ExcelUtil.read(file, SampleRow.class, (row, context) -> {
		}, read::addAll, 1);

		assertThat(read).hasSize(2);
	}

	@Test
	void writeWithoutWriteHandlers() {
		assertThat(ExcelUtil.write(this.rows, SampleRow.class)).isNotEmpty();
	}

	@Test
	void writeRegistersWriteHandlers() {
		AtomicInteger disposedCells = new AtomicInteger();
		CellWriteHandler handler = new CellWriteHandler() {
			@Override
			public void afterCellDispose(CellWriteHandlerContext context) {
				disposedCells.incrementAndGet();
			}
		};

		byte[] bytes = ExcelUtil.write(this.rows, SampleRow.class, handler);

		assertThat(bytes).isNotEmpty();
		assertThat(disposedCells).hasPositiveValue();
	}

	@Test
	void writeWithTemplateWritesRowsWithoutHead() throws IOException {
		byte[] template = ExcelUtil.write("template", List.of(), SampleRow.class);

		byte[] filled = ExcelUtil.write(new ByteArrayInputStream(template), this.rows, SampleRow.class, "sheet1");

		assertThat(filled).isNotEmpty();
		// 模板本身带表头行,needHead(false) 的数据行写在表头之后,因此仍按第 1 行为表头读取
		List<SampleRow> read = readAll(filled, 1);
		assertThat(read).hasSize(2);
		assertThat(read.getFirst().getName()).isEqualTo("butterfly");
	}

	@Test
	void writeToServletResponseSetsDownloadHeaders() {
		MockHttpServletResponse response = new MockHttpServletResponse();

		ExcelUtil.write(response, fieldsMap(), "报表", List.of(rowMap()));

		assertThat(response.getContentType()).isEqualTo(XLSX_CONTENT_TYPE);
		assertThat(response.getHeader("Content-Disposition"))
			.isEqualTo("attachment;filename=" + URLEncoder.encode("报表", StandardCharsets.UTF_8) + ".xlsx");
		assertThat(response.getContentAsByteArray()).isNotEmpty();
	}

	@Test
	void writeToServletResponseWrapsFailure() {
		MockHttpServletResponse response = new MockHttpServletResponse() {
			@Override
			public ServletOutputStream getOutputStream() {
				throw new IllegalStateException("stream broken");
			}
		};

		assertThatThrownBy(() -> ExcelUtil.write(response, fieldsMap(), "报表", List.of(rowMap())))
			.isInstanceOf(BusinessException.class)
			.hasMessage("导出失败！")
			.hasCauseInstanceOf(IllegalStateException.class);
	}

	@Test
	void writeWrapsConverterFailure() {
		assertThatThrownBy(() -> ExcelUtil.write("broken", List.of(new BrokenRow()), BrokenRow.class))
			.isInstanceOf(BusinessException.class);
	}

	@Test
	void getResponseWrapsBytesAsDownload() {
		byte[] bytes = ExcelUtil.write("sample", this.rows, SampleRow.class);

		ResponseEntity<byte[]> response = ExcelUtil.getResponse("报表", bytes);

		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(response.getBody()).isEqualTo(bytes);
		assertThat(response.getHeaders().getFirst("Content-Disposition"))
			.isEqualTo("attachment; filename=\"" + URLEncoder.encode("报表", StandardCharsets.UTF_8) + ".xlsx\"");
		assertThat(response.getHeaders().getFirst("Content-Type")).isEqualTo(XLSX_CONTENT_TYPE);
	}

	private MockMultipartFile multipartFile() {
		byte[] bytes = ExcelUtil.write("sample", this.rows, SampleRow.class);
		return new MockMultipartFile("file", "sample.xlsx", XLSX_CONTENT_TYPE, bytes);
	}

	private List<SampleRow> readAll(byte[] bytes, int headRowNumber) throws IOException {
		List<SampleRow> read = new ArrayList<>();
		ExcelUtil.read(bytes, SampleRow.class, (row, context) -> {
		}, read::addAll, headRowNumber, "sheet1");
		return read;
	}

	private Map<String, String> fieldsMap() {
		Map<String, String> fields = new LinkedHashMap<>();
		fields.put("name", "姓名");
		fields.put("age", "年龄");
		return fields;
	}

	private Map<String, Object> rowMap() {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("name", "butterfly");
		row.put("age", 3);
		return row;
	}

	static class BrokenRow {

		@ExcelProperty(value = "值", converter = BrokenConverter.class)
		private String value = "v";

		String getValue() {
			return this.value;
		}

	}

	static class BrokenConverter implements Converter<String> {

		@Override
		public Class<?> supportJavaTypeKey() {
			return String.class;
		}

		@Override
		public CellDataTypeEnum supportExcelTypeKey() {
			return CellDataTypeEnum.STRING;
		}

		@Override
		public WriteCellData<?> convertToExcelData(String value, ExcelContentProperty contentProperty,
				GlobalConfiguration globalConfiguration) {
			throw new IllegalStateException("broken converter");
		}

		@Override
		public WriteCellData<?> convertToExcelData(WriteConverterContext<String> context) {
			throw new IllegalStateException("broken converter");
		}

	}

}
