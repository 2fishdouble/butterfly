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

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 内存账号实现:没有提供 {@link UserAccountRepository} 时的保底方案,只用于开发与测试.
 * <p>
 * 相比 SPI 多出 {@link #save} 与 {@link #count},供默认管理员初始化使用;进程重启后账号全部丢失。 生产环境请自己实现
 * {@link UserAccountRepository},框架不会向业务实现写入任何数据。本实现线程安全。
 */
public class InMemoryUserAccountRepository implements UserAccountRepository {

	private final ConcurrentMap<String, UserAccount> accounts = new ConcurrentHashMap<>();

	/**
	 * 按登录名查询账号.
	 * @param username 登录名
	 * @return 命中时返回账号,不存在时返回空
	 */
	@Override
	public Optional<UserAccount> findByUsername(String username) {
		return Optional.ofNullable(this.accounts.get(username));
	}

	/**
	 * 已有账号数量.
	 * @return 账号总数
	 */
	public long count() {
		return this.accounts.size();
	}

	/**
	 * 保存账号,同一登录名以新值覆盖旧值.
	 * @param account 待保存的账号
	 */
	public void save(UserAccount account) {
		this.accounts.put(account.username(), account);
	}

}
