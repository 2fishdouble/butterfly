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
import cn.idev.excel.context.AnalysisContextImpl;
import cn.idev.excel.enums.RowTypeEnum;
import cn.idev.excel.exception.ExcelDataConvertException;
import cn.idev.excel.metadata.GlobalConfiguration;
import cn.idev.excel.read.metadata.ReadWorkbook;
import cn.idev.excel.read.metadata.holder.ReadRowHolder;
import cn.idev.excel.support.ExcelTypeEnum;
import io.github.butterfly.core.BusinessException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 覆盖读监听器的逐行校验、整批回调与解析异常转换.
 */
class ExcelDataListenerTests {

	private final AnalysisContext context = newContext();

	@Test
	void invokesVerifyCallbackPerRowThenCachesRows() {
		List<String> verified = new ArrayList<>();
		List<List<String>> batches = new ArrayList<>();
		ExcelDataListener<String> listener = new ExcelDataListener<>((row, ctx) -> verified.add(row), batches::add);

		listener.invoke("a", this.context);
		listener.invoke("b", this.context);

		assertThat(verified).containsExactly("a", "b");
		assertThat(batches).isEmpty();

		listener.doAfterAllAnalysed(this.context);

		assertThat(batches).containsExactly(List.of("a", "b"));
	}

	@Test
	void passesTheSameCacheListToCallback() {
		List<List<String>> batches = new ArrayList<>();
		ExcelDataListener<String> listener = new ExcelDataListener<>((row, ctx) -> {
		}, batches::add);

		listener.invoke("a", this.context);
		listener.doAfterAllAnalysed(this.context);

		assertThat(batches.getFirst()).isSameAs(listener.cacheList);
	}

	@Test
	void verifyCallbackReceivesTheAnalysisContext() {
		List<AnalysisContext> contexts = new ArrayList<>();
		ExcelDataListener<String> listener = new ExcelDataListener<>((row, ctx) -> contexts.add(ctx), (rows) -> {
		});

		listener.invoke("a", this.context);

		assertThat(contexts).containsExactly(this.context);
	}

	@Test
	void verifyFailurePropagatesAndRowIsNotCached() {
		ExcelDataListener<String> listener = new ExcelDataListener<>((row, ctx) -> {
			throw new IllegalArgumentException("bad row");
		}, (rows) -> {
		});

		assertThatThrownBy(() -> listener.invoke("a", this.context)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("bad row");
		assertThat(listener.cacheList).isEmpty();
	}

	@Test
	void convertsExcelDataConvertExceptionWithRowAndColumn() {
		ExcelDataConvertException exception = new ExcelDataConvertException(2, 3, null, null, "convert failed",
				new IllegalArgumentException("bad cell"));

		assertThatThrownBy(() -> listener().onException(exception, this.context)).isInstanceOf(BusinessException.class)
			.hasMessage("第3行，第4列：bad cell")
			.hasCause(exception);
	}

	@Test
	void convertsExcelDataConvertExceptionWithoutCauseMessage() {
		ExcelDataConvertException exception = new ExcelDataConvertException(0, 0, null, null, "convert failed");

		assertThatThrownBy(() -> listener().onException(exception, this.context)).isInstanceOf(BusinessException.class)
			.hasMessage("第1行，第1列：null");
	}

	@Test
	void convertsIllegalArgumentExceptionWithRowPrefix() {
		AnalysisContext rowContext = contextWithRowIndex(4);

		assertThatThrownBy(() -> listener().onException(new IllegalArgumentException("标题不合法"), rowContext))
			.isInstanceOf(BusinessException.class)
			.hasMessage("第5行，标题不合法")
			.hasCauseInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void convertsIllegalArgumentExceptionWithFallbackMessage() {
		AnalysisContext rowContext = contextWithRowIndex(0);

		assertThatThrownBy(() -> listener().onException(new IllegalArgumentException(), rowContext))
			.isInstanceOf(BusinessException.class)
			.hasMessage("第1行，解析异常");
	}

	@Test
	void convertsIllegalArgumentExceptionWithoutRowIndex() {
		AnalysisContext rowContext = contextWithRowIndex(null);

		assertThatThrownBy(() -> listener().onException(new IllegalArgumentException("没有行号"), rowContext))
			.isInstanceOf(BusinessException.class)
			.hasMessage("没有行号");
	}

	@Test
	void rethrowsOtherRuntimeExceptionAsIs() {
		IllegalStateException exception = new IllegalStateException("boom");

		assertThatThrownBy(() -> listener().onException(exception, this.context)).isSameAs(exception);
	}

	@Test
	void ignoresCheckedException() {
		assertThatCode(() -> listener().onException(new IOException("io"), this.context)).doesNotThrowAnyException();
	}

	@Test
	void invokeHeadIsANoOp() {
		assertThatCode(() -> listener().invokeHead(Map.of(), this.context)).doesNotThrowAnyException();
	}

	private ExcelDataListener<String> listener() {
		return new ExcelDataListener<>((row, ctx) -> {
		}, (rows) -> {
		});
	}

	private static AnalysisContext newContext() {
		return new AnalysisContextImpl(new ReadWorkbook(), ExcelTypeEnum.XLSX);
	}

	private static AnalysisContext contextWithRowIndex(@Nullable Integer rowIndex) {
		AnalysisContextImpl rowContext = new AnalysisContextImpl(new ReadWorkbook(), ExcelTypeEnum.XLSX);
		rowContext
			.readRowHolder(new ReadRowHolder(rowIndex, RowTypeEnum.DATA, new GlobalConfiguration(), new HashMap<>()));
		return rowContext;
	}

}
