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
import io.github.butterfly.core.R;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 登录接口的单元测试:返回值走统一响应结构,参数为空直接拒绝.
 */
class LoginControllerTests {

	private final LoginService loginService = mock(LoginService.class);

	private final LoginController loginController = new LoginController(this.loginService);

	@Test
	void returnsTokenInUnifiedResponse() {
		LoginResult loginResult = new LoginResult("a.b.c", "Bearer", 7200L, "admin");
		given(this.loginService.login("admin", "admin")).willReturn(loginResult);

		R<LoginResult> response = this.loginController.login(new LoginRequest("admin", "admin"));

		assertThat(response.getCode()).isEqualTo(200);
		assertThat(response.getIsSuccess()).isTrue();
		assertThat(response.getData()).isEqualTo(loginResult);
	}

	@Test
	void rejectsBlankCredentials() {
		assertThatThrownBy(() -> this.loginController.login(new LoginRequest(" ", "admin")))
			.isInstanceOf(BusinessException.class)
			.hasMessage("用户名和密码不能为空");
		assertThatThrownBy(() -> this.loginController.login(new LoginRequest("admin", null)))
			.isInstanceOf(BusinessException.class)
			.hasMessage("用户名和密码不能为空");
	}

}
