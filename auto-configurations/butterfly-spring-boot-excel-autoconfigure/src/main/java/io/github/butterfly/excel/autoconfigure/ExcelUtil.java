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

import cn.idev.excel.FastExcelFactory;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.write.builder.ExcelWriterSheetBuilder;
import cn.idev.excel.write.handler.WriteHandler;
import io.github.butterfly.core.BusinessException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 基于 FastExcel 的 Excel 读写工具类,供使用方直接调用.
 * <p>
 * 读:{@code read} 系列重载把 Excel 解析为实体列表,逐行执行校验回调,全部解析完成后整批回调; 写:{@code write} 系列重载写出为 xlsx
 * 字节数组(支持模板写出与注册 {@link WriteHandler}), 或直接写入 {@link HttpServletResponse}
 * 完成下载;{@link #getResponse} 把字节数组包装成带下载 响应头的 {@link ResponseEntity}。
 * <p>
 * 本类为静态工具类,不提供实例;写出失败时,模板写出重载由 FastExcel 直接抛出异常,其余写出与 下载重载包装为 {@link BusinessException}。
 */
@Slf4j
public final class ExcelUtil {

	private ExcelUtil() {
	}

	/**
	 * 读取上传的 Excel 并解析为实体列表,表头行号使用 FastExcel 默认值(第 1 行).
	 * @param <T> 行数据映射的实体类型
	 * @param file 上传的 Excel 文件,读取其输入流
	 * @param clazz 实体类型,字段按 FastExcel 注解(如 {@code @ExcelProperty})与字段类型选择转换器
	 * @param verifyCallback 逐行校验回调,参数为当前行对象与解析上下文;需要中断读取时可在其中 抛出异常
	 * @param callback 全部解析完成后的整批回调,参数为按读取顺序排列的行列表
	 * @throws IOException 获取上传文件输入流失败,或读取过程中产生 IO 错误时抛出
	 */
	public static <T> void read(MultipartFile file, Class<T> clazz, BiConsumer<T, AnalysisContext> verifyCallback,
			Consumer<List<T>> callback) throws IOException {
		FastExcelFactory.read(file.getInputStream(), clazz, new ExcelDataListener<>(verifyCallback, callback))
			.sheet()
			.doRead();
	}

	/**
	 * 读取上传的 Excel 并解析为实体列表,可指定表头所在行号.
	 * @param <T> 行数据映射的实体类型
	 * @param file 上传的 Excel 文件,读取其输入流
	 * @param clazz 实体类型,字段按 FastExcel 注解(如 {@code @ExcelProperty})与字段类型选择转换器
	 * @param verifyCallback 逐行校验回调,参数为当前行对象与解析上下文;需要中断读取时可在其中 抛出异常
	 * @param callback 全部解析完成后的整批回调,参数为按读取顺序排列的行列表
	 * @param headRowNumber 表头所在行号,从 1 开始,其后各行按数据行解析
	 * @throws IOException 获取上传文件输入流失败,或读取过程中产生 IO 错误时抛出
	 */
	public static <T> void read(MultipartFile file, Class<T> clazz, BiConsumer<T, AnalysisContext> verifyCallback,
			Consumer<List<T>> callback, int headRowNumber) throws IOException {
		FastExcelFactory.read(file.getInputStream(), clazz, new ExcelDataListener<>(verifyCallback, callback))
			.sheet()
			.headRowNumber(headRowNumber)
			.doRead();
	}

	/**
	 * 从字节数组读取 Excel 的指定工作表并解析为实体列表,可指定表头所在行号.
	 * @param <T> 行数据映射的实体类型
	 * @param bytes 待读取的 Excel 文件内容
	 * @param clazz 实体类型,字段按 FastExcel 注解(如 {@code @ExcelProperty})与字段类型选择转换器
	 * @param verifyCallback 逐行校验回调,参数为当前行对象与解析上下文;需要中断读取时可在其中 抛出异常
	 * @param callback 全部解析完成后的整批回调,参数为按读取顺序排列的行列表
	 * @param headRowNumber 表头所在行号,从 1 开始,其后各行按数据行解析
	 * @param sheet 工作表名称
	 * @throws IOException 方法签名声明的受检异常,用于与其它 {@code read} 重载保持一致;本实现基于 内存中的
	 * {@link ByteArrayInputStream},通常不会真正抛出
	 */
	public static <T> void read(byte[] bytes, Class<T> clazz, BiConsumer<T, AnalysisContext> verifyCallback,
			Consumer<List<T>> callback, int headRowNumber, String sheet) throws IOException {
		FastExcelFactory.read(new ByteArrayInputStream(bytes), clazz, new ExcelDataListener<>(verifyCallback, callback))
			.sheet(sheet)
			.headRowNumber(headRowNumber)
			.doRead();
	}

	/**
	 * 把数据写出为 xlsx 字节数组,工作表名固定为 {@code sheet1},并写出表头行.
	 * @param <T> 数据行的实体类型
	 * @param fileName 目标文件名;当前实现未使用该参数
	 * @param data 待写出的数据,可为空列表
	 * @param clazz 实体类型,字段按 FastExcel 注解(如 {@code @ExcelProperty})生成表头与单元格
	 * @return xlsx 文件的字节内容
	 * @throws BusinessException 写出失败时抛出,消息取自底层异常(底层异常消息为 {@code null} 时 该消息也为
	 * {@code null})
	 */
	public static <T> byte[] write(String fileName, List<T> data, Class<T> clazz) {
		try {
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			FastExcelFactory.write(outputStream, clazz).autoCloseStream(Boolean.FALSE).sheet("sheet1").doWrite(data);
			return outputStream.toByteArray();
		}
		catch (Exception ex) {
			log.error("下载文件失败：", ex);
			throw new BusinessException(ex.getMessage());
		}
	}

	/**
	 * 以给定模板为基底写出数据,不额外写出表头行,内容写入指定的工作表.
	 * @param <T> 数据行的实体类型
	 * @param templateStream 模板输入流,由 FastExcel 的 {@code withTemplate} 读取
	 * @param data 待写出的数据,需与模板中的列对应
	 * @param clazz 实体类型,字段按 FastExcel 注解(如 {@code @ExcelProperty})映射到模板列
	 * @param sheetName 工作表名称
	 * @return 写出后的 xlsx 文件字节内容
	 */
	public static <T> byte[] write(InputStream templateStream, List<T> data, Class<T> clazz, String sheetName) {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		FastExcelFactory.write(outputStream, clazz)
			.withTemplate(templateStream)
			.autoCloseStream(Boolean.FALSE)
			.sheet(sheetName)
			.needHead(false)
			.doWrite(data);
		return outputStream.toByteArray();
	}

	/**
	 * 把数据写出为 xlsx 字节数组,工作表名固定为 {@code sheet1},并注册若干写处理器.
	 * @param <T> 数据行的实体类型
	 * @param data 待写出的数据,可为空列表
	 * @param clazz 实体类型,字段按 FastExcel 注解(如 {@code @ExcelProperty})生成表头与单元格
	 * @param writeHandlers 写出过程中生效的处理器,按传入顺序注册,可为空(不注册任何处理器)
	 * @return xlsx 文件的字节内容
	 */
	public static <T> byte[] write(List<T> data, Class<T> clazz, WriteHandler... writeHandlers) {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		ExcelWriterSheetBuilder sheet1 = FastExcelFactory.write(outputStream, clazz)
			.autoCloseStream(Boolean.FALSE)
			.sheet("sheet1");
		for (WriteHandler writeHandler : writeHandlers) {
			sheet1.registerWriteHandler(writeHandler);
		}
		sheet1.doWrite(data);
		return outputStream.toByteArray();
	}

	/**
	 * 把数据按字段映射直接写入响应输出流,完成浏览器下载.
	 * <p>
	 * 响应头固定为 xlsx 的 content-type 与 {@code Content-Disposition: attachment},文件名经
	 * {@link URLEncoder} 以 UTF-8 编码后追加 {@code .xlsx};表头取 {@code fieldsMap} 的值, 每行的单元格按
	 * {@code fieldsMap} 的键顺序从行数据中取值,因此需要列顺序稳定时应传入有序 Map。
	 * @param response 当前 HTTP 响应对象,写出后会被 flush
	 * @param fieldsMap 字段名到表头名称的映射,同时决定列顺序
	 * @param fileName 下载文件名,不含扩展名(扩展名由本方法补 {@code .xlsx})
	 * @param data 待导出的行数据,每行为字段名到单元格值的映射,缺失的字段写出空单元格
	 * @throws BusinessException 写出或刷新响应输出流失败时抛出
	 */
	public static void write(HttpServletResponse response, Map<String, String> fieldsMap, String fileName,
			List<Map<String, Object>> data) {
		response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
		response.setHeader("Content-Disposition",
				"attachment;filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + ".xlsx");

		List<List<String>> head = new ArrayList<>();
		for (String headerName : fieldsMap.values()) {
			head.add(Collections.singletonList(headerName));
		}

		List<List<Object>> dataList = new ArrayList<>();
		for (Map<String, Object> rowMap : data) {
			List<Object> rowData = new ArrayList<>();
			for (String fieldKey : fieldsMap.keySet()) {
				rowData.add(rowMap.get(fieldKey));
			}
			dataList.add(rowData);
		}

		try {
			FastExcelFactory.write(response.getOutputStream()).head(head).sheet("sheet1").doWrite(dataList);
			response.getOutputStream().flush();
		}
		catch (Exception ex) {
			throw new BusinessException("导出失败！", ex);
		}
	}

	/**
	 * 把已写出的 Excel 字节内容包装为下载响应实体,状态码为 200.
	 * <p>
	 * 响应头包含 {@code Content-Disposition: attachment; filename="..."} 与 xlsx 的
	 * content-type,文件名经 {@link URLEncoder} 以 UTF-8 编码后追加 {@code .xlsx}。
	 * @param fileName 下载文件名,不含扩展名
	 * @param write xlsx 文件的字节内容,通常来自 {@code write} 系列方法的返回值
	 * @return 携带下载响应头与文件内容的 {@link ResponseEntity}
	 */
	public static ResponseEntity<byte[]> getResponse(String fileName, byte[] write) {
		String attachmentName = String.format("attachment; filename=\"%s.xlsx\"",
				URLEncoder.encode(fileName, StandardCharsets.UTF_8));
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.add("Content-Disposition", attachmentName);
		httpHeaders.add("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
		return ResponseEntity.ok().headers(httpHeaders).body(write);
	}

}
