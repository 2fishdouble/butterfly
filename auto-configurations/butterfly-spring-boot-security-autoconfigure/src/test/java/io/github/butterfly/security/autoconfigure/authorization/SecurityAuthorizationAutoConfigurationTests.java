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

package io.github.butterfly.security.autoconfigure.authorization;

import io.github.butterfly.security.autoconfigure.SamplePermission;
import io.github.butterfly.security.autoconfigure.SecurityPermissionAutoConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 授权自动配置测试:权限授予关系的读写,以及 {@link RequirePermission} 是否真的参与方法拦截.
 */
class SecurityAuthorizationAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(SecurityPermissionAutoConfiguration.class,
				SecurityAuthorizationAutoConfiguration.class))
		.withPropertyValues("butterfly.security.permission.scan-packages=" + SamplePermission.class.getPackageName())
		.withUserConfiguration(ProtectedServiceConfiguration.class);

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void inMemoryRepositoryIsTheDefault() {
		this.contextRunner.run((context) -> assertThat(context.getBean(PermissionRepository.class))
			.isInstanceOf(InMemoryPermissionRepository.class));
	}

	@Test
	void grantAllExposesEveryCollectedPermission() {
		this.contextRunner.run((context) -> {
			InMemoryPermissionRepository repository = (InMemoryPermissionRepository) context
				.getBean(PermissionRepository.class);

			repository.grantAll(1L);
			repository.grant(2L, 100L, 200L);

			assertThat(repository.findPermissionIdsByUserId(1L)).containsExactly(100L, 101L, 200L, 201L);
			assertThat(repository.findPermissionIdsByUserId(2L)).containsExactly(100L, 200L);
			assertThat(repository.findPermissionIdsByUserId(3L)).isEmpty();
		});
	}

	@Test
	void requirePermissionAllowsGrantedAuthority() {
		authenticateWith("100");
		this.contextRunner.run((context) -> assertThatCode(() -> context.getBean(ProtectedService.class).read())
			.doesNotThrowAnyException());
	}

	@Test
	void requirePermissionDeniesMissingAuthority() {
		authenticateWith("200");
		this.contextRunner.run((context) -> assertThatThrownBy(() -> context.getBean(ProtectedService.class).read())
			.isInstanceOf(AccessDeniedException.class));
	}

	@Test
	void requirePermissionDeniesUnauthenticatedCaller() {
		this.contextRunner.run((context) -> assertThatThrownBy(() -> context.getBean(ProtectedService.class).read())
			// 未登录时由 Spring Security 的方法拦截器直接抛认证异常,HTTP 层对应 401
			.isInstanceOf(AuthenticationCredentialsNotFoundException.class));
	}

	@Test
	void requirePermissionNeedsEveryDeclaredId() {
		authenticateWith("100");
		this.contextRunner.run((context) -> {
			ProtectedService service = context.getBean(ProtectedService.class);

			assertThatThrownBy(service::write).isInstanceOf(AccessDeniedException.class);
			authenticateWith("100", "101");
			assertThatCode(service::write).doesNotThrowAnyException();
		});
	}

	@Test
	void methodSecurityCanBeDisabled() {
		this.contextRunner.withPropertyValues("butterfly.security.authorization.method-security-enabled=false")
			.run((context) -> assertThatCode(() -> context.getBean(ProtectedService.class).read())
				.doesNotThrowAnyException());
	}

	@Test
	void authorizationCanBeDisabled() {
		this.contextRunner.withPropertyValues("butterfly.security.authorization.enabled=false").run((context) -> {
			assertThat(context).doesNotHaveBean(PermissionRepository.class);
			assertThatCode(() -> context.getBean(ProtectedService.class).read()).doesNotThrowAnyException();
		});
	}

	private static void authenticateWith(String... authorityIds) {
		List<GrantedAuthority> authorities = Arrays.stream(authorityIds)
			.map(SimpleGrantedAuthority::new)
			.map(GrantedAuthority.class::cast)
			.toList();
		SecurityContextHolder.getContext()
			.setAuthentication(new TestingAuthenticationToken("tester", "n/a", authorities));
	}

	@Configuration(proxyBeanMethods = false)
	static class ProtectedServiceConfiguration {

		@Bean
		ProtectedService protectedService() {
			return new ProtectedService();
		}

	}

	/**
	 * 被保护的示例服务,方法上标了 {@link RequirePermission};数值对应 {@code SamplePermission} 的权限 id.
	 */
	static class ProtectedService {

		@RequirePermission(100L)
		public String read() {
			return "ok";
		}

		@RequirePermission({ 100L, 101L })
		public String write() {
			return "ok";
		}

	}

}
