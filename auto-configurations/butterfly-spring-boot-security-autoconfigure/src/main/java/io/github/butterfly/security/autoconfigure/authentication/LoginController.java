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
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录接口:默认 {@code POST /login},请求体为 {@code {"username":"...","password":"..."}}.
 * <p>
 * 路径由 {@code butterfly.security.authentication.login-path} 决定(注解上直接取值,不需要重新注册 handler),
 * 该路径在安全过滤链里被放行。返回统一响应 {@link R},成功时 {@code data} 为 {@link LoginResult}。
 * <p>
 * 只提供登录:JWT 是无状态的,「登出」由调用方丢弃 token 完成;需要服务端主动作废时,请在
 * {@code butterfly.security.authentication.jwt.expiration} 之外自行加黑名单。
 */
@RestController
public class LoginController {

	private final LoginService loginService;

	/**
	 * 创建登录接口.
	 * @param loginService 登录流程
	 */
	public LoginController(LoginService loginService) {
		this.loginService = loginService;
	}

	/**
	 * 校验账号密码并签发 token.
	 * @param request 登录请求体,用户名与密码都可能为 {@code null}(请求体缺字段时)
	 * @return 成功时携带 token、token 类型、有效期(秒)与登录名
	 * @throws BusinessException 用户名或密码为空时抛出
	 */
	@PostMapping("${butterfly.security.authentication.login-path:/login}")
	public R<LoginResult> login(@RequestBody LoginRequest request) {
		return R.success(this.loginService.login(requireText(request.username()), requireText(request.password())));
	}

	private static String requireText(@Nullable String value) {
		if (value == null || value.isBlank()) {
			throw new BusinessException("用户名和密码不能为空");
		}
		return value;
	}

}
