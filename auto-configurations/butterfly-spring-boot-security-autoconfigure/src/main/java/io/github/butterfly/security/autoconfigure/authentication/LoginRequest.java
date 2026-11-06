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
 * 登录请求体.
 * <p>
 * 两个字段都允许为 {@code null}:请求体里少写字段时 Jackson 会给出 {@code null},由接口统一校验成 「用户名和密码不能为空」。
 *
 * @param username 登录名
 * @param password 明文密码,仅用于与密码散列比对,不会被保存
 */
public record LoginRequest(@Nullable String username, @Nullable String password) {

}
