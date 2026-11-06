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

/**
 * 安全认证包:负责回答「你是谁」.
 * <p>
 * {@link io.github.butterfly.security.autoconfigure.authentication.UserAccountRepository}
 * 由使用方实现 (查自己的用户表、LDAP 或外部单点登录),框架提供内存实现
 * {@link io.github.butterfly.security.autoconfigure.authentication.InMemoryUserAccountRepository}
 * 保底; {@link io.github.butterfly.security.autoconfigure.authentication.LoginController}
 * 校验密码后,由
 * {@link io.github.butterfly.security.autoconfigure.authentication.JwtTokenService} 签发
 * HS256 的 JWT。
 * <p>
 * 装配入口为
 * {@link io.github.butterfly.security.autoconfigure.authentication.SecurityAuthenticationAutoConfiguration},
 * 它把 JWT 编解码、登录接口、无状态过滤链与 401/403 的统一响应一起注册,并把权限 id 从 token 的 {@code authorities} 声明还原成
 * {@code GrantedAuthority}。
 */
@NullMarked
package io.github.butterfly.security.autoconfigure.authentication;

import org.jspecify.annotations.NullMarked;
