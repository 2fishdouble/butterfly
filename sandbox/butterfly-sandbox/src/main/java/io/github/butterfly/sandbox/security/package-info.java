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
 * 沙箱的认证/授权示例包.
 * <p>
 * 演示「框架只管机制、业务提供数据」的接法:账号由
 * {@link io.github.butterfly.sandbox.security.DemoUserAccountRepository} 提供, 「用户 → 权限
 * id」由 {@link io.github.butterfly.sandbox.security.DemoPermissionRepository} 提供,
 * 两者都是内存实现,真实项目换成查自己的用户表与角色-权限关系表即可。
 */
@NullMarked
package io.github.butterfly.sandbox.security;

import org.jspecify.annotations.NullMarked;
