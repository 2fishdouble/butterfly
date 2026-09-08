package io.github.butterfly.excel.autoconfigure;

import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.exception.ExcelDataConvertException;
import cn.idev.excel.metadata.data.ReadCellData;
import cn.idev.excel.read.listener.ReadListener;
import io.github.butterfly.core.BusinessException;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;


@Slf4j
public class ExcelDataListener<T> implements ReadListener<T> {

    List<T> cacheList = new ArrayList<>();

    Consumer<List<T>> callback;

    BiConsumer<T, AnalysisContext> verifyCallback;


    public ExcelDataListener(BiConsumer<T, AnalysisContext> verifyCallback, Consumer<List<T>> callback) {
        this.callback = callback;
        this.verifyCallback = verifyCallback;
    }

    @Override
    public void invokeHead(Map<Integer, ReadCellData<?>> headMap, AnalysisContext context) {
        ReadListener.super.invokeHead(headMap, context);
    }

    @Override
    public void invoke(T t, AnalysisContext analysisContext) {
        verifyCallback.accept(t, analysisContext);

        cacheList.add(t);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {
        callback.accept(cacheList);
    }

    @Override
    public void onException(Exception exception, AnalysisContext context) throws Exception {
        switch (exception) {
            case ExcelDataConvertException convertException -> {
                String causeMessage = findFirstCauseMessage(convertException);
                int row = convertException.getRowIndex() + 1;
                int column = convertException.getColumnIndex() + 1;
                throw new BusinessException("第" + row + "行，第" + column + "列：" + causeMessage, convertException);
            }
            case IllegalArgumentException illegalArgumentException -> {
                Integer rowIndex = context.readRowHolder().getRowIndex();
                String message = illegalArgumentException.getMessage() == null ? "解析异常" : illegalArgumentException.getMessage();
                String prefix = rowIndex == null ? "" : "第" + (rowIndex + 1) + "行，";
                throw new BusinessException(prefix + message, illegalArgumentException);
            }
            case RuntimeException ignored -> throw exception;
            default -> {
            }
        }
    }

    private String findFirstCauseMessage(Throwable throwable) {
        Throwable cause = throwable.getCause();
        while (cause != null) {
            String message = cause.getMessage();
            if (message != null && !message.isBlank()) {
                return message;
            }
            cause = cause.getCause();
        }
        return null;
    }
}
