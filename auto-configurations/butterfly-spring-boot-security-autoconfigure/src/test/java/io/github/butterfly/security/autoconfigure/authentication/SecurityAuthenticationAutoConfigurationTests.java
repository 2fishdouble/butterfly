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

import io.github.butterfly.core.BusinessException;
import io.github.butterfly.security.autoconfigure.SamplePermission;
import io.github.butterfly.security.autoconfigure.SecurityPermissionAutoConfiguration;
import io.github.butterfly.security.autoconfigure.authorization.SecurityAuthorizationAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 认证自动配置的装配与登录流程测试:默认管理员、JWT 声明、错误分支与开关.
 * <p>
 * 这里只启动非 Web 上下文,因此不涉及过滤链;过滤链的端到端行为见 {@code SecurityEndToEndTests}。
 */
class SecurityAuthenticationAutoConfigurationTests {

	private static final String SECRET = "butterfly-test-secret-0123456789abcdef";

	private static final String SCAN_PACKAGES = "butterfly.security.permission.scan-packages="
			+ SamplePermission.class.getPackageName();

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(SecurityPermissionAutoConfiguration.class,
				SecurityAuthorizationAutoConfiguration.class, SecurityAuthenticationAutoConfiguration.class))
		.withPropertyValues(SCAN_PACKAGES, "butterfly.security.authentication.jwt.secret=" + SECRET);

	@Test
	void registersAuthenticationComponents() {
		this.contextRunner.run((context) -> {
			assertThat(context).hasSingleBean(UserAccountRepository.class);
			assertThat(context.getBean(UserAccountRepository.class)).isInstanceOf(InMemoryUserAccountRepository.class);
			assertThat(context).hasSingleBean(PasswordEncoder.class);
			assertThat(context).hasSingleBean(JwtEncoder.class);
			assertThat(context).hasSingleBean(JwtDecoder.class);
			assertThat(context).hasSingleBean(JwtTokenService.class);
			assertThat(context).hasSingleBean(LoginService.class);
		});
	}

	@Test
	void defaultAdminCanLoginAndCarriesCollectedPermissions() {
		this.contextRunner.run((context) -> {
			LoginService loginService = context.getBean(LoginService.class);

			LoginResult result = loginService.login("admin", "admin");

			assertThat(result.token()).isNotBlank();
			assertThat(result.tokenType()).isEqualTo("Bearer");
			assertThat(result.expiresIn()).isEqualTo(7200L);
			assertThat(result.username()).isEqualTo("admin");

			Jwt jwt = context.getBean(JwtDecoder.class).decode(result.token());
			assertThat(jwt.getSubject()).isEqualTo("admin");
			assertThat(jwt.<Long>getClaim(JwtTokenService.CLAIM_USER_ID))
				.isEqualTo(DefaultAdminInitializer.DEFAULT_ADMIN_ID);
			List<String> expectedPermissions = Arrays.stream(SamplePermission.values())
				.map((permission) -> String.valueOf(permission.getId()))
				.toList();
			assertThat(jwt.getClaimAsStringList(JwtTokenService.CLAIM_AUTHORITIES))
				.containsExactlyInAnyOrderElementsOf(expectedPermissions);
		});
	}

	@Test
	void wrongPasswordIsRejectedWithGenericMessage() {
		this.contextRunner.run((context) -> {
			LoginService loginService = context.getBean(LoginService.class);

			assertThatThrownBy(() -> loginService.login("admin", "wrong-password"))
				.isInstanceOf(BadCredentialsException.class)
				.hasMessage("用户名或密码错误");
		});
	}

	@Test
	void unknownUserIsRejectedWithSameMessage() {
		this.contextRunner.run((context) -> {
			LoginService loginService = context.getBean(LoginService.class);

			assertThatThrownBy(() -> loginService.login("nobody", "admin")).isInstanceOf(BadCredentialsException.class)
				.hasMessage("用户名或密码错误");
		});
	}

	@Test
	void disabledAccountIsRejected() {
		this.contextRunner.run((context) -> {
			InMemoryUserAccountRepository userAccounts = (InMemoryUserAccountRepository) context
				.getBean(UserAccountRepository.class);
			PasswordEncoder passwordEncoder = context.getBean(PasswordEncoder.class);
			userAccounts.save(new UserAccount(2L, "bob", passwordEncoder.encode("bob-password"), "Bob", false));

			LoginService loginService = context.getBean(LoginService.class);

			assertThatThrownBy(() -> loginService.login("bob", "bob-password")).isInstanceOf(BusinessException.class)
				.hasMessage("账号已被禁用, 请联系管理员");
		});
	}

	@Test
	void enabledCustomAccountCanLogin() {
		this.contextRunner.run((context) -> {
			InMemoryUserAccountRepository userAccounts = (InMemoryUserAccountRepository) context
				.getBean(UserAccountRepository.class);
			PasswordEncoder passwordEncoder = context.getBean(PasswordEncoder.class);
			userAccounts.save(new UserAccount(3L, "carol", passwordEncoder.encode("secret"), "Carol", true));

			LoginResult result = context.getBean(LoginService.class).login("carol", "secret");

			assertThat(result.username()).isEqualTo("carol");
			Jwt jwt = context.getBean(JwtDecoder.class).decode(result.token());
			assertThat(jwt.<Long>getClaim(JwtTokenService.CLAIM_USER_ID)).isEqualTo(3L);
			assertThat(jwt.getClaimAsStringList(JwtTokenService.CLAIM_AUTHORITIES)).isEmpty();
		});
	}

	@Test
	void shortSecretFailsFast() {
		this.contextRunner.withPropertyValues("butterfly.security.authentication.jwt.secret=too-short")
			.run((context) -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).hasStackTraceContaining("至少需要 32 字节");
			});
	}

	@Test
	void missingSecretFallsBackToRandomKey() {
		this.contextRunner.withPropertyValues("butterfly.security.authentication.jwt.secret=").run((context) -> {
			assertThat(context).hasSingleBean(JwtDecoder.class);
			assertThat(context.getBean(LoginService.class).login("admin", "admin").token()).isNotBlank();
		});
	}

	@Test
	void defaultAdminInitializationCanBeDisabled() {
		this.contextRunner.withPropertyValues("butterfly.security.authentication.init-default-admin=false")
			.run((context) -> assertThat(
					((InMemoryUserAccountRepository) context.getBean(UserAccountRepository.class)).count())
				.isZero());
	}

	@Test
	void authenticationCanBeDisabled() {
		this.contextRunner.withPropertyValues("butterfly.security.authentication.enabled=false").run((context) -> {
			assertThat(context).doesNotHaveBean(LoginService.class);
			assertThat(context).doesNotHaveBean(JwtDecoder.class);
			assertThat(context).doesNotHaveBean(UserAccountRepository.class);
		});
	}

	@Test
	void customRepositoryReplacesDefaultAndIsNeverWritten() {
		UserAccountRepository custom = (username) -> Optional.empty();
		this.contextRunner.withBean(UserAccountRepository.class, () -> custom).run((context) -> {
			assertThat(context.getBean(UserAccountRepository.class)).isSameAs(custom);
			// 业务实现不会被框架写入默认管理员,所以仍然没有任何账号
			assertThatThrownBy(() -> context.getBean(LoginService.class).login("admin", "admin"))
				.isInstanceOf(BadCredentialsException.class);
		});
	}

}
