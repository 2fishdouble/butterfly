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
 * 安全授权包:负责回答「已登录用户能做什么」.
 * <p>
 * {@link io.github.butterfly.security.autoconfigure.authorization.PermissionRepository}
 * 由使用方实现, 返回用户拥有的权限 id(框架不建角色表);框架提供内存实现
 * {@link io.github.butterfly.security.autoconfigure.authorization.InMemoryPermissionRepository}
 * 保底。
 * <p>
 * {@link io.github.butterfly.security.autoconfigure.authorization.RequirePermission} 与
 * Spring Security 的 {@code @PreAuthorize} 一起由
 * {@link io.github.butterfly.security.autoconfigure.authorization.SecurityAuthorizationAutoConfiguration}
 * 接入方法级授权;权限 id 以字符串形式作为 {@code GrantedAuthority} 参与判定,{@code 100} 对应
 * {@code hasAuthority('100')}。
 */
@NullMarked
package io.github.butterfly.security.autoconfigure.authorization;

import org.jspecify.annotations.NullMarked;
