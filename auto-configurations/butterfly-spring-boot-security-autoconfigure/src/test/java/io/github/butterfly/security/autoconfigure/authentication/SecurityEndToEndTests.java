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

import io.github.butterfly.security.autoconfigure.SamplePermission;
import io.github.butterfly.security.autoconfigure.SecurityPermissionAutoConfiguration;
import io.github.butterfly.security.autoconfigure.authorization.SecurityAuthorizationAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.FilterChainProxy;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 端到端验证安全过滤链:登录路径放行、其余路径未带 token 时 401、带合法 token 时放行.
 * <p>
 * 直接驱动 {@link FilterChainProxy} 而不是 MockMvc:这里要验证的是过滤链本身, 不需要 Spring MVC 的 handler 基础设施。
 */
class SecurityEndToEndTests {

	private static final String SECRET = "butterfly-test-secret-0123456789abcdef";

	private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(SecurityAutoConfiguration.class,
				ServletWebSecurityAutoConfiguration.class, SecurityPermissionAutoConfiguration.class,
				SecurityAuthorizationAutoConfiguration.class, SecurityAuthenticationAutoConfiguration.class))
		.withPropertyValues("butterfly.security.permission.scan-packages=" + SamplePermission.class.getPackageName(),
				"butterfly.security.authentication.jwt.secret=" + SECRET);

	@Test
	void rejectsAnonymousRequestWithUnifiedJsonBody() {
		this.contextRunner.run((context) -> {
			MockHttpServletResponse response = doFilter(context.getBean(FilterChainProxy.class), "GET", "/api/profile",
					null);

			assertThat(response.getStatus()).isEqualTo(401);
			assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains("\"code\":100000")
				.contains("未登录或登录已过期");
		});
	}

	@Test
	void permitsLoginPathWithoutToken() {
		this.contextRunner.run((context) -> {
			AtomicBoolean reachedEndpoint = new AtomicBoolean(false);

			doFilter(context.getBean(FilterChainProxy.class), "POST", "/login", null, reachedEndpoint);

			assertThat(reachedEndpoint).isTrue();
		});
	}

	@Test
	void permitsRequestWithValidToken() {
		this.contextRunner.run((context) -> {
			String token = context.getBean(LoginService.class).login("admin", "admin").token();
			AtomicBoolean reachedEndpoint = new AtomicBoolean(false);

			MockHttpServletResponse response = doFilter(context.getBean(FilterChainProxy.class), "GET", "/api/profile",
					"Bearer " + token, reachedEndpoint);

			assertThat(reachedEndpoint).isTrue();
			assertThat(response.getStatus()).isEqualTo(200);
		});
	}

	@Test
	void rejectsRequestWithGarbageToken() {
		this.contextRunner.run((context) -> {
			AtomicBoolean reachedEndpoint = new AtomicBoolean(false);

			MockHttpServletResponse response = doFilter(context.getBean(FilterChainProxy.class), "GET", "/api/profile",
					"Bearer not-a-jwt", reachedEndpoint);

			assertThat(reachedEndpoint).isFalse();
			assertThat(response.getStatus()).isEqualTo(401);
		});
	}

	private static MockHttpServletResponse doFilter(FilterChainProxy filterChainProxy, String method, String path,
			@Nullable String authorization) {
		return doFilter(filterChainProxy, method, path, authorization, new AtomicBoolean(false));
	}

	private static MockHttpServletResponse doFilter(FilterChainProxy filterChainProxy, String method, String path,
			@Nullable String authorization, AtomicBoolean reachedEndpoint) {
		MockHttpServletRequest request = new MockHttpServletRequest(method, path);
		if (authorization != null) {
			request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
		}
		MockHttpServletResponse response = new MockHttpServletResponse();
		try {
			filterChainProxy.doFilter(request, response,
					(servletRequest, servletResponse) -> reachedEndpoint.set(true));
		}
		catch (Exception ex) {
			throw new IllegalStateException("过滤链执行失败", ex);
		}
		return response;
	}

}
