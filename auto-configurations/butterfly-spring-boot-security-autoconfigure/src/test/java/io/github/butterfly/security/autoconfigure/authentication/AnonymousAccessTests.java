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
import io.github.butterfly.security.autoconfigure.SamplePermission;
import io.github.butterfly.security.autoconfigure.SecurityPermissionAutoConfiguration;
import io.github.butterfly.security.autoconfigure.authorization.SecurityAuthorizationAutoConfiguration;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AnonymousAccess} 的端到端测试:匿名请求能不能穿过过滤链.
 * <p>
 * 这里把 MVC 也拉起来({@code @EnableWebMvc}),因为判定依赖 {@code RequestMappingHandlerMapping}
 * 把请求解析成目标方法;断言方式是直接驱动 {@link FilterChainProxy},看请求最终有没有走到 Controller。
 */
class AnonymousAccessTests {

	private static final String SECRET = "butterfly-test-secret-0123456789abcdef";

	private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(SecurityAutoConfiguration.class,
				ServletWebSecurityAutoConfiguration.class, SecurityPermissionAutoConfiguration.class,
				SecurityAuthorizationAutoConfiguration.class, SecurityAuthenticationAutoConfiguration.class))
		.withPropertyValues("butterfly.security.permission.scan-packages=" + SamplePermission.class.getPackageName(),
				"butterfly.security.authentication.jwt.secret=" + SECRET)
		.withUserConfiguration(MvcConfiguration.class);

	@Test
	void anonymousRequestToAnnotatedMethodIsAllowed() {
		this.contextRunner.run((context) -> {
			AtomicBoolean reachedEndpoint = new AtomicBoolean(false);

			MockHttpServletResponse response = doFilter(context.getBean(FilterChainProxy.class), "/secure/public", null,
					reachedEndpoint);

			assertThat(reachedEndpoint).isTrue();
			assertThat(response.getStatus()).isEqualTo(200);
		});
	}

	@Test
	void anonymousRequestToUnannotatedMethodIsRejected() {
		this.contextRunner.run((context) -> {
			AtomicBoolean reachedEndpoint = new AtomicBoolean(false);

			MockHttpServletResponse response = doFilter(context.getBean(FilterChainProxy.class), "/secure/private",
					null, reachedEndpoint);

			assertThat(reachedEndpoint).isFalse();
			assertThat(response.getStatus()).isEqualTo(401);
		});
	}

	@Test
	void classLevelAnnotationAllowsEveryMethod() {
		this.contextRunner.run((context) -> {
			AtomicBoolean reachedEndpoint = new AtomicBoolean(false);

			doFilter(context.getBean(FilterChainProxy.class), "/open/second", null, reachedEndpoint);

			assertThat(reachedEndpoint).isTrue();
		});
	}

	@Test
	void authenticatedRequestIsStillAllowedOnProtectedPath() {
		this.contextRunner.run((context) -> {
			String token = context.getBean(LoginService.class).login("admin", "admin").token();
			AtomicBoolean reachedEndpoint = new AtomicBoolean(false);

			MockHttpServletResponse response = doFilter(context.getBean(FilterChainProxy.class), "/secure/private",
					"Bearer " + token, reachedEndpoint);

			assertThat(reachedEndpoint).isTrue();
			assertThat(response.getStatus()).isEqualTo(200);
		});
	}

	private static MockHttpServletResponse doFilter(FilterChainProxy filterChainProxy, String path,
			@Nullable String authorization, AtomicBoolean reachedEndpoint) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
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

	@Configuration(proxyBeanMethods = false)
	@EnableWebMvc
	static class MvcConfiguration {

		@Bean
		SecureController secureController() {
			return new SecureController();
		}

		@Bean
		OpenController openController() {
			return new OpenController();
		}

	}

	/**
	 * 方法级注解:只有标了 {@link AnonymousAccess} 的接口允许匿名.
	 */
	@RestController
	static class SecureController {

		@GetMapping("/secure/public")
		@AnonymousAccess
		R<String> publicEndpoint() {
			return R.success("public");
		}

		@GetMapping("/secure/private")
		R<String> privateEndpoint() {
			return R.success("private");
		}

	}

	/**
	 * 类级注解:整个 Controller 都允许匿名.
	 */
	@RestController
	@AnonymousAccess
	static class OpenController {

		@GetMapping("/open/first")
		R<String> first() {
			return R.success("first");
		}

		@GetMapping("/open/second")
		R<String> second() {
			return R.success("second");
		}

	}

}
