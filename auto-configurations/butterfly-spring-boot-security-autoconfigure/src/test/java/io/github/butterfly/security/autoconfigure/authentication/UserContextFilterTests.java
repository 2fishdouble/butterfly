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

import jakarta.servlet.ServletException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link UserContextFilter} 的行为:请求内可见、请求后清理、匿名不写入、异常也清理.
 */
class UserContextFilterTests {

	private final UserContextFilter filter = new UserContextFilter();

	@AfterEach
	void clearAll() {
		SecurityContextHolder.clearContext();
		UserContext.clear();
	}

	@Test
	void populatesContextInsideChainAndClearsAfterwards() throws Exception {
		SecurityContextHolder.getContext().setAuthentication(jwtAuthentication("alice", 7L, List.of("100", "101")));
		// 类型实参标 @Nullable:lambda 执行之前这里确实还没有值
		AtomicReference<@Nullable LoginUser> captured = new AtomicReference<>();

		this.filter.doFilter(new MockHttpServletRequest("GET", "/api/me"), new MockHttpServletResponse(),
				(request, response) -> captured.set(UserContext.require()));

		LoginUser user = Objects.requireNonNull(captured.get());
		assertThat(user.userId()).isEqualTo(7L);
		assertThat(user.username()).isEqualTo("alice");
		assertThat(user.permissionIds()).containsExactlyInAnyOrder(100L, 101L);
		assertThat(UserContext.isPresent()).as("请求结束后必须清理,避免线程池串号").isFalse();
	}

	@Test
	void clearsContextEvenWhenChainFails() {
		SecurityContextHolder.getContext().setAuthentication(jwtAuthentication("alice", 7L, List.of("100")));

		assertThatThrownBy(() -> this.filter.doFilter(new MockHttpServletRequest("GET", "/api/me"),
				new MockHttpServletResponse(), (request, response) -> {
					throw new ServletException("boom");
				}))
			.isInstanceOf(ServletException.class);

		assertThat(UserContext.isPresent()).isFalse();
	}

	@Test
	void anonymousRequestLeavesContextEmpty() throws Exception {
		SecurityContextHolder.getContext()
			.setAuthentication(new AnonymousAuthenticationToken("anonymous", "anonymous",
					List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
		AtomicReference<@Nullable Boolean> present = new AtomicReference<>();

		this.filter.doFilter(new MockHttpServletRequest("GET", "/api/public/hello"), new MockHttpServletResponse(),
				(request, response) -> present.set(UserContext.isPresent()));

		assertThat(present.get()).isFalse();
	}

	@Test
	void tokenWithoutUidClaimIsIgnored() throws Exception {
		Jwt jwt = Jwt.withTokenValue("token")
			.header("alg", "HS256")
			.subject("alice")
			.claim(JwtTokenService.CLAIM_AUTHORITIES, List.of("100"))
			.build();
		SecurityContextHolder.getContext()
			.setAuthentication(new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("100"))));
		AtomicReference<@Nullable Boolean> present = new AtomicReference<>();

		this.filter.doFilter(new MockHttpServletRequest("GET", "/api/me"), new MockHttpServletResponse(),
				(request, response) -> present.set(UserContext.isPresent()));

		assertThat(present.get()).isFalse();
	}

	private static JwtAuthenticationToken jwtAuthentication(String username, long userId, List<String> permissionIds) {
		Jwt jwt = Jwt.withTokenValue("token")
			.header("alg", "HS256")
			.subject(username)
			.claim(JwtTokenService.CLAIM_USER_ID, userId)
			.claim(JwtTokenService.CLAIM_AUTHORITIES, permissionIds)
			.build();
		List<GrantedAuthority> authorities = permissionIds.stream()
			.map(SimpleGrantedAuthority::new)
			.map(GrantedAuthority.class::cast)
			.toList();
		return new JwtAuthenticationToken(jwt, authorities);
	}

}
