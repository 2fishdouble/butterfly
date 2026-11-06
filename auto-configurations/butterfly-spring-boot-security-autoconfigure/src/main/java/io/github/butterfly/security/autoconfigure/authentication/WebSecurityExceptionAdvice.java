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

package io.github.butterfly.security.autoconfigure.authentication;

import io.github.butterfly.core.R;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 方法级授权抛出的异常到 HTTP 状态码的映射.
 * <p>
 * {@link JsonSecurityExceptionHandler} 只负责过滤链这一层(未带 token、token 无效);而
 * {@code @PreAuthorize} 与 {@code @RequirePermission} 的拒绝发生在 Controller 方法内部,异常会先被 Spring
 * MVC 的 {@code @ExceptionHandler} 机制处理掉,走不到过滤链的 {@code AccessDeniedHandler}。 不额外处理的话,全局兜底
 * {@code HandleExceptionAdvice} 会把它变成「HTTP 200 + Access Denied」的响应。
 * <p>
 * 这里按异常类型给出与过滤链一致的响应体与状态码:
 * <ul>
 * <li>权限不足 → {@code 403} + 「没有访问该资源的权限」;</li>
 * <li>方法级校验时没有登录 → {@code 401} + 「未登录或登录已过期」。</li>
 * </ul>
 * 登录接口抛出的 {@code BadCredentialsException} 不在这里处理,仍由业务异常兜底返回它自己的提示。
 * <p>
 * {@code @Order} 必须是最高优先级:多个 {@code @ControllerAdvice} 并存时,Spring MVC 按 advice 的顺序取
 * 「第一个能处理该异常」的方法,而不是全局比较异常的继承距离。不排在最前面,就会被框架的 {@code @ExceptionHandler(Throwable.class)}
 * 兜底抢走,变成「HTTP 200 + Access Denied」。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class WebSecurityExceptionAdvice {

	/**
	 * 已登录但权限不足时返回 403.
	 * @param exception 授权异常
	 * @return 状态码 403 的统一失败响应
	 */
	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<R<Object>> handleAccessDenied(AccessDeniedException exception) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(R.error("没有访问该资源的权限"));
	}

	/**
	 * 方法级校验时没有登录(或凭据不足)时返回 401.
	 * @param exception 认证异常
	 * @return 状态码 401 的统一失败响应
	 */
	@ExceptionHandler({ AuthenticationCredentialsNotFoundException.class, InsufficientAuthenticationException.class })
	public ResponseEntity<R<Object>> handleUnauthenticated(AuthenticationException exception) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(R.error("未登录或登录已过期"));
	}

}
