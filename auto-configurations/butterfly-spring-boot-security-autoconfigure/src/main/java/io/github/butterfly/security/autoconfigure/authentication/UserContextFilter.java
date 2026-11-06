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

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 把 JWT 里的用户信息解析成 {@link LoginUser} 放进 {@link UserContext},请求结束后清理.
 * <p>
 * 位置很关键:它必须排在 {@code BearerTokenAuthenticationFilter} 之后,因为要等 token 校验通过、 认证信息写进
 * {@code SecurityContext} 之后才拿得到 {@link JwtAuthenticationToken}。框架的过滤链已经按这个顺序
 * 装配好了,使用方不需要自己注册。
 * <p>
 * 匿名访问(没有 token)时不会写入上下文,{@link UserContext#isPresent()} 为 {@code false}。
 */
public class UserContextFilter extends OncePerRequestFilter {

	/**
	 * 解析认证信息并写入线程上下文,然后继续过滤链;无论是否抛异常,结束时都会清理上下文.
	 * @param request 当前请求
	 * @param response 当前响应
	 * @param filterChain 过滤链
	 * @throws ServletException 过滤链执行失败时抛出
	 * @throws IOException 读写请求/响应失败时抛出
	 */
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		try {
			populate(SecurityContextHolder.getContext().getAuthentication());
			filterChain.doFilter(request, response);
		}
		finally {
			UserContext.clear();
		}
	}

	/**
	 * 只处理 JWT 认证;其它认证方式(例如将来接入的自定义 Authentication)不写入上下文.
	 * @param authentication 当前认证信息,可能为 {@code null}
	 */
	private static void populate(@Nullable Authentication authentication) {
		if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
			toLoginUser(jwtAuthenticationToken.getToken()).ifPresent(UserContext::set);
		}
	}

	private static Optional<LoginUser> toLoginUser(Jwt jwt) {
		return userId(jwt).map((id) -> new LoginUser(id, subject(jwt), permissionIds(jwt)));
	}

	private static Optional<Long> userId(Jwt jwt) {
		return Optional.ofNullable(jwt.getClaim(JwtTokenService.CLAIM_USER_ID))
			.filter(Number.class::isInstance)
			.map(Number.class::cast)
			.map(Number::longValue);
	}

	private static String subject(Jwt jwt) {
		return Optional.ofNullable(jwt.getSubject()).orElse("");
	}

	private static Set<Long> permissionIds(Jwt jwt) {
		return Optional.ofNullable(jwt.getClaimAsStringList(JwtTokenService.CLAIM_AUTHORITIES))
			.stream()
			.flatMap(List::stream)
			.flatMap((value) -> parsePermissionId(value).stream())
			.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private static Optional<Long> parsePermissionId(String value) {
		try {
			return Optional.of(Long.valueOf(value));
		}
		catch (NumberFormatException ex) {
			return Optional.empty();
		}
	}

}
