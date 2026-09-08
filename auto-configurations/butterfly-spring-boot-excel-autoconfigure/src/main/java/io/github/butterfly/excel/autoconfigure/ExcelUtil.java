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


@Slf4j
public class ExcelUtil {
    private ExcelUtil() {
    }

    public static <T> void read(MultipartFile file, Class<T> clazz, BiConsumer<T, AnalysisContext> verifyCallback, Consumer<List<T>> callback) throws IOException {
        FastExcelFactory.read(file.getInputStream(), clazz, new ExcelDataListener<>(verifyCallback, callback)).sheet().doRead();
    }

    public static <T> void read(MultipartFile file, Class<T> clazz, BiConsumer<T, AnalysisContext> verifyCallback, Consumer<List<T>> callback, int headRowNumber) throws IOException {
        FastExcelFactory.read(file.getInputStream(), clazz, new ExcelDataListener<>(verifyCallback, callback)).sheet().headRowNumber(headRowNumber).doRead();
    }

    public static <T> void read(byte[] bytes, Class<T> clazz, BiConsumer<T, AnalysisContext> verifyCallback, Consumer<List<T>> callback, int headRowNumber, String sheet) throws IOException {
        FastExcelFactory.read(new ByteArrayInputStream(bytes), clazz, new ExcelDataListener<>(verifyCallback, callback)).sheet(sheet).headRowNumber(headRowNumber).doRead();
    }

    public static <T> byte[] write(String fileName, List<T> data, Class<T> clazz) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            FastExcelFactory.write(outputStream, clazz).autoCloseStream(Boolean.FALSE).sheet("sheet1").doWrite(data);
            return outputStream.toByteArray();
        } catch (Exception e) {
            log.error("下载文件失败：", e);
            throw new BusinessException(e.getMessage());
        }
    }

    public static <T> byte[] write(InputStream templateStream, List<T> data, Class<T> clazz, String sheetName) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        FastExcelFactory.write(outputStream, clazz).withTemplate(templateStream).autoCloseStream(Boolean.FALSE).sheet(sheetName).needHead(false).doWrite(data);
        return outputStream.toByteArray();
    }

    public static <T> byte[] write(List<T> data, Class<T> clazz, WriteHandler... writeHandlers) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ExcelWriterSheetBuilder sheet1 = FastExcelFactory.write(outputStream, clazz)
                .autoCloseStream(Boolean.FALSE).sheet("sheet1");
        for (WriteHandler writeHandler : writeHandlers) {
            sheet1.registerWriteHandler(writeHandler);
        }
        sheet1.doWrite(data);
        return outputStream.toByteArray();
    }


    public static void write(HttpServletResponse response,
                                            Map<String, String> fieldsMap,
                                            String fileName,
                                            List<Map<String, Object>> data) {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + ".xlsx");

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
            FastExcelFactory.write(response.getOutputStream())
                    .head(head)
                    .sheet("sheet1")
                    .doWrite(dataList);
            response.getOutputStream().flush();
        } catch (Exception e) {
            throw new BusinessException("导出失败！", e);
        }
    }


    public static ResponseEntity<byte[]> getResponse(String fileName, byte[] write){
        String attachmentName = String.format("attachment; filename=\"%s.xlsx\"", URLEncoder.encode(fileName, StandardCharsets.UTF_8));
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("Content-Disposition", attachmentName);
        httpHeaders.add("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        return ResponseEntity.ok().headers(httpHeaders).body(write);
    }
}
