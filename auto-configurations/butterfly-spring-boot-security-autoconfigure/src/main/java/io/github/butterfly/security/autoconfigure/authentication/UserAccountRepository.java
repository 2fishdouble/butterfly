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

/**
 * 账号读取入口(SPI):框架只要求「按登录名查到一个能校验密码的账号」.
 * <p>
 * 账号与角色属于业务数据,字段、表结构、存储位置(自己库里的 {@code user} 表、LDAP、外部单点登录)都应由使用方
 * 决定,因此框架不建表、不写库,只定义这一个查询方法。实现类声明成 Bean 即可接管,例如:
 *
 * <pre>{@code
 * public Optional<UserAccount> findByUsername(String username) {
 *     // SELECT id, username, password, nickname, enabled FROM sys_user WHERE username = ?
 *     return Optional.ofNullable(loadFromMyTable(username));
 * }
 * }</pre>
 * <p>
 * {@link UserAccount#password()} 必须是 BCrypt 散列,与框架注册的 {@code PasswordEncoder} 保持一致。
 * 没有提供实现时退回 {@link InMemoryUserAccountRepository},只用于开发与测试,并会在启动时打印告警。
 */
@FunctionalInterface
public interface UserAccountRepository {

	/**
	 * 按登录名查询账号.
	 * @param username 登录名
	 * @return 命中时返回账号,不存在时返回空
	 */
	Optional<UserAccount> findByUsername(String username);

}
