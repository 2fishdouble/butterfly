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

package io.github.butterfly.autoconfigure;

import io.github.butterfly.core.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常兜底处理器,把未被其它处理器消费的 {@link Throwable} 统一包装为 {@link R#error(String)}
 * 响应体返回,便于前端按统一结构解析错误.
 * <p>
 * 仅在 Servlet Web 应用中生效,且要求 classpath 上存在 {@link RestControllerAdvice} (spring-web
 * 为可选依赖,缺失时本类不会被注册)。
 */
@RestControllerAdvice
@ConditionalOnClass(RestControllerAdvice.class)
@ConditionalOnWebApplication(type = Type.SERVLET)
@Slf4j
public class HandleExceptionAdvice {

	/**
	 * 兜底处理所有未被其它 {@code @ExceptionHandler} 匹配的异常与错误.
	 * <p>
	 * 返回值是 code 为 {@code 100000}、{@code isSuccess} 为 {@code false}、 {@code msg} 取自
	 * {@link Throwable#getMessage()}(可能为 {@code null})的失败响应, 同时以 error 级别输出完整堆栈。
	 * @param t 捕获到的异常或错误
	 * @return 携带异常信息的统一失败响应
	 */
	@ExceptionHandler(Throwable.class)
	public R<Object> handleThrowable(Throwable t) {
		R<Object> error = R.error(t.getMessage());
		log.error("error: ", t);
		return error;
	}

}
