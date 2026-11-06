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
import io.github.butterfly.security.autoconfigure.authorization.PermissionRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

/**
 * 登录流程:校验账号密码,查出该账号的权限 id,再签发 JWT.
 * <p>
 * 用户名不存在与密码错误返回同一句提示,避免通过错误信息枚举账号;账号被禁用时返回明确提示。 权限 id 来自
 * {@link PermissionRepository},授权功能关闭时为 {@code null},此时 token 不带任何权限。
 */
public class LoginService {

	private static final String INVALID_CREDENTIALS = "用户名或密码错误";

	private static final String DISABLED_ACCOUNT = "账号已被禁用, 请联系管理员";

	private final UserAccountRepository userAccounts;

	private final PasswordEncoder passwordEncoder;

	private final JwtTokenService jwtTokenService;

	private final @Nullable PermissionRepository permissionRepository;

	/**
	 * 创建登录服务.
	 * @param userAccounts 账号读写入口
	 * @param passwordEncoder 密码编码器,用于比对 BCrypt 散列
	 * @param jwtTokenService JWT 签发服务
	 * @param permissionRepository 权限授予关系查询,授权功能关闭时为 {@code null}
	 */
	public LoginService(UserAccountRepository userAccounts, PasswordEncoder passwordEncoder,
			JwtTokenService jwtTokenService, @Nullable PermissionRepository permissionRepository) {
		this.userAccounts = userAccounts;
		this.passwordEncoder = passwordEncoder;
		this.jwtTokenService = jwtTokenService;
		this.permissionRepository = permissionRepository;
	}

	/**
	 * 校验账号密码并签发 token.
	 * @param username 登录名
	 * @param password 明文密码
	 * @return 登录结果,含 token、有效期与登录名
	 * @throws BadCredentialsException 用户名不存在或密码错误时抛出,提示统一为「用户名或密码错误」,避免枚举账号
	 * @throws BusinessException 账号存在但已被禁用时抛出
	 */
	public LoginResult login(String username, String password) {
		UserAccount account = this.userAccounts.findByUsername(username)
			.orElseThrow(() -> new BadCredentialsException(INVALID_CREDENTIALS));
		if (!account.enabled()) {
			throw new BusinessException(DISABLED_ACCOUNT);
		}
		if (!this.passwordEncoder.matches(password, account.password())) {
			throw new BadCredentialsException(INVALID_CREDENTIALS);
		}
		List<Long> permissionIds = (this.permissionRepository != null)
				? this.permissionRepository.findPermissionIdsByUserId(account.id()) : List.of();
		String token = this.jwtTokenService.issue(account, permissionIds);
		return new LoginResult(token, this.jwtTokenService.getTokenType(), this.jwtTokenService.getExpiresIn(),
				account.username());
	}

}
