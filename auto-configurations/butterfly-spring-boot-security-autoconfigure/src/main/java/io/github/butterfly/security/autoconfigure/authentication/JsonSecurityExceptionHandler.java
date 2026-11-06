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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 把「未登录」与「无权限」也包装成统一响应 {@link R},避免前端拿到 Spring Security 默认的空响应体.
 * <p>
 * 未登录(含 token 缺失、失效、过期)返回 {@code 401},已登录但权限不足返回 {@code 403};两者响应体都是 {@code R.error(提示)}
 * 的 JSON,与业务异常的响应结构一致。
 */
public class JsonSecurityExceptionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

	private static final String UNAUTHORIZED_MESSAGE = "未登录或登录已过期";

	private static final String FORBIDDEN_MESSAGE = "没有访问该资源的权限";

	private final ObjectMapper objectMapper;

	/**
	 * 创建处理器.
	 * @param objectMapper 用于把 {@link R} 序列化为 JSON 的 Jackson 映射器
	 */
	public JsonSecurityExceptionHandler(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	/**
	 * 认证失败(未带 token 或 token 无效)时返回 401.
	 * @param request 当前请求
	 * @param response 当前响应
	 * @param authException 触发本次跳转的认证异常
	 * @throws IOException 写出响应体失败时抛出
	 */
	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		write(response, HttpStatus.UNAUTHORIZED, UNAUTHORIZED_MESSAGE);
	}

	/**
	 * 已认证但权限不足时返回 403.
	 * @param request 当前请求
	 * @param response 当前响应
	 * @param accessDeniedException 触发本次跳转的授权异常
	 * @throws IOException 写出响应体失败时抛出
	 */
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		write(response, HttpStatus.FORBIDDEN, FORBIDDEN_MESSAGE);
	}

	private void write(HttpServletResponse response, HttpStatus status, String message) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write(this.objectMapper.writeValueAsString(R.error(message)));
	}

}
