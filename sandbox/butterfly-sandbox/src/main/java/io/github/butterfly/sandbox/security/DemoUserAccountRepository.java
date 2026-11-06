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

package io.github.butterfly.sandbox.security;

import io.github.butterfly.security.autoconfigure.authentication.UserAccount;
import io.github.butterfly.security.autoconfigure.authentication.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 沙箱用的账号实现:内存里放两个账号,密码用框架注册的 BCrypt 编码器加密.
 * <p>
 * 演示「框架不建 user 表,账号由业务提供」:真实项目把 {@link #findByUsername(String)} 换成查自己的用户表即可 (字段只要凑出
 * id、登录名、BCrypt 密码散列、是否启用)。账号数据只在内存里,重启即恢复成这两个账号。
 */
@Component
public class DemoUserAccountRepository implements UserAccountRepository {

	/**
	 * 管理员用户 id,拥有全部权限.
	 */
	public static final long ADMIN_ID = 1L;

	/**
	 * 访客用户 id,只有商品列表权限.
	 */
	public static final long GUEST_ID = 2L;

	private final Map<String, UserAccount> accounts = new ConcurrentHashMap<>();

	/**
	 * 创建两个演示账号:admin/admin、guest/guest.
	 * @param passwordEncoder 框架注册的密码编码器,用来生成 BCrypt 散列
	 */
	public DemoUserAccountRepository(PasswordEncoder passwordEncoder) {
		this.accounts.put("admin", new UserAccount(ADMIN_ID, "admin", passwordEncoder.encode("admin"), "管理员", true));
		this.accounts.put("guest", new UserAccount(GUEST_ID, "guest", passwordEncoder.encode("guest"), "访客", true));
	}

	/**
	 * 按登录名查询账号.
	 * @param username 登录名
	 * @return 命中时返回账号,不存在时返回空
	 */
	@Override
	public Optional<UserAccount> findByUsername(String username) {
		return Optional.ofNullable(this.accounts.get(username));
	}

}
