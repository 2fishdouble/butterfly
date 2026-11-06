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
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 方法级授权异常到 HTTP 状态的映射测试.
 */
class WebSecurityExceptionAdviceTests {

	private final WebSecurityExceptionAdvice advice = new WebSecurityExceptionAdvice();

	@Test
	void accessDeniedBecomes403() {
		ResponseEntity<R<Object>> response = this.advice.handleAccessDenied(new AccessDeniedException("Access Denied"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		R<Object> body = Objects.requireNonNull(response.getBody());
		assertThat(body.getCode()).isEqualTo(100000);
		assertThat(body.getIsSuccess()).isFalse();
		assertThat(body.getMsg()).isEqualTo("没有访问该资源的权限");
	}

	@Test
	void unauthenticatedBecomes401() {
		ResponseEntity<R<Object>> response = this.advice
			.handleUnauthenticated(new AuthenticationCredentialsNotFoundException("not authenticated"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		R<Object> body = Objects.requireNonNull(response.getBody());
		assertThat(body.getCode()).isEqualTo(100000);
		assertThat(body.getMsg()).isEqualTo("未登录或登录已过期");
	}

}
