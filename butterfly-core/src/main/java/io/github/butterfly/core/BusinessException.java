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

package io.github.butterfly.core;

/**
 * 业务异常, 继承 {@link RuntimeException}.
 * <p>
 * 用于业务流程中可预期的失败场景(参数校验不通过、状态非法、重复提交等), 因此无需在方法签名上 声明 {@code throws};Servlet Web
 * 环境下由全局异常处理器转换为失败响应 {@link R#error(String)}, 异常描述即响应中的 {@code msg}.
 */
public class BusinessException extends RuntimeException {

	/**
	 * 使用异常描述构造业务异常.
	 * @param message 异常描述, 由 {@link Throwable#getMessage()} 返回
	 */
	public BusinessException(String message) {
		super(message);
	}

	/**
	 * 使用异常描述与根因构造业务异常.
	 * @param message 异常描述, 由 {@link Throwable#getMessage()} 返回
	 * @param cause 引发本异常的根因, 由 {@link Throwable#getCause()} 返回
	 */
	public BusinessException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * 使用根因构造业务异常, 异常描述取根因的 {@link Throwable#toString()}(形如 {@code 类名: 描述}).
	 * @param cause 引发本异常的根因, 由 {@link Throwable#getCause()} 返回
	 */
	public BusinessException(Throwable cause) {
		super(cause);
	}

}
