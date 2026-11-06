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

import org.jspecify.annotations.Nullable;

/**
 * 认证用的账号信息.
 * <p>
 * {@code password} 存放的是 BCrypt 散列,不是明文;{@code id} 会写进 JWT 的 {@code uid} 声明,授权侧按它查
 * 用户拥有的权限。
 *
 * @param id 用户 id,对应 {@code user.id}
 * @param username 登录名,全局唯一
 * @param password BCrypt 密码散列
 * @param nickname 昵称,可为 {@code null}
 * @param enabled 是否启用,禁用后不能登录
 */
public record UserAccount(long id, String username, String password, @Nullable String nickname, boolean enabled) {

}
