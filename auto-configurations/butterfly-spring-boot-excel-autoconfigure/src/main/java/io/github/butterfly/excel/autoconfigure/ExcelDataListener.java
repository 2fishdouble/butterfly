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

import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.exception.ExcelDataConvertException;
import cn.idev.excel.metadata.data.ReadCellData;
import cn.idev.excel.read.listener.ReadListener;
import io.github.butterfly.core.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * FastExcel 读监听器:逐行校验并缓存,全部解析完成后把整批数据一次性交给回调.
 * <p>
 * 每读到一行先执行 {@code verifyCallback}(校验不通过时可在其中抛异常中断读取),再放入内部缓存; {@link #doAfterAllAnalysed}
 * 时以整个缓存列表调用 {@code callback}。解析异常经 {@link #onException} 转换为
 * {@link BusinessException},以便统一返回带行号、列号的错误信息。
 *
 * @param <T> 每行数据映射的实体类型
 */
@Slf4j
public class ExcelDataListener<T> implements ReadListener<T> {

	List<T> cacheList = new ArrayList<>();

	Consumer<List<T>> callback;

	BiConsumer<T, AnalysisContext> verifyCallback;

	/**
	 * 创建读监听器.
	 * @param verifyCallback 逐行校验回调,参数为当前行对象与解析上下文;需要中断读取时可在其中 抛出异常
	 * @param callback 全部解析完成后的整批回调,参数为按读取顺序排列的行列表
	 */
	public ExcelDataListener(BiConsumer<T, AnalysisContext> verifyCallback, Consumer<List<T>> callback) {
		this.callback = callback;
		this.verifyCallback = verifyCallback;
	}

	/**
	 * 读取表头时的回调,此处直接委托 {@link ReadListener#invokeHead} 的默认实现,不做额外处理.
	 * @param headMap 表头列索引到单元格数据的映射
	 * @param context 当前解析上下文
	 */
	@Override
	public void invokeHead(Map<Integer, ReadCellData<?>> headMap, AnalysisContext context) {
		ReadListener.super.invokeHead(headMap, context);
	}

	/**
	 * 每解析出一行时调用:先执行逐行校验回调,再将该行加入缓存.
	 * @param t 当前行映射出的实体对象
	 * @param analysisContext 当前解析上下文
	 */
	@Override
	public void invoke(T t, AnalysisContext analysisContext) {
		this.verifyCallback.accept(t, analysisContext);

		this.cacheList.add(t);
	}

	/**
	 * 全部行解析完成时调用:把缓存中的整批数据传给回调.缓存列表按解析顺序累积,回调后不清空.
	 * @param analysisContext 当前解析上下文
	 */
	@Override
	public void doAfterAllAnalysed(AnalysisContext analysisContext) {
		this.callback.accept(this.cacheList);
	}

	/**
	 * 解析异常的统一处理,用模式匹配的 {@code switch} 按异常类型转换.
	 * <ul>
	 * <li>{@link ExcelDataConvertException} 转换为 {@link BusinessException},消息形如
	 * {@code 第x行，第y列：...},行列号均从 1 开始,补充说明取异常链上第一个非空的消息;</li>
	 * <li>{@link IllegalArgumentException} 转换为 {@link BusinessException},能取到行号时 加上
	 * {@code 第x行，} 前缀,消息为 {@code null} 时用 {@code 解析异常} 兜底;</li>
	 * <li>其它 {@link RuntimeException} 原样抛出;</li>
	 * <li>其余异常(含受检异常)不抛出,直接忽略。</li>
	 * </ul>
	 * @param exception 读取过程中抛出的异常
	 * @param context 当前解析上下文,用于获取当前行号
	 * @throws Exception 异常为其它 {@link RuntimeException} 时原样抛出;为
	 * {@link ExcelDataConvertException} 或 {@link IllegalArgumentException} 时抛出转换后的
	 * {@link BusinessException}
	 */
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
				String message = (illegalArgumentException.getMessage() == null) ? "解析异常"
						: illegalArgumentException.getMessage();
				String prefix = (rowIndex != null) ? "第" + (rowIndex + 1) + "行，" : "";
				throw new BusinessException(prefix + message, illegalArgumentException);
			}
			case RuntimeException ignored -> throw exception;
			default -> {
			}
		}
	}

	private @Nullable String findFirstCauseMessage(Throwable throwable) {
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
