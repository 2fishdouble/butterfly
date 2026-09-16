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
 * 权限自动配置包.
 * <p>
 * 提供启动时的权限字典自动收集能力:使用方的权限/分组枚举分别实现
 * {@link io.github.butterfly.security.autoconfigure.IPermission} 与
 * {@link io.github.butterfly.security.autoconfigure.IPermissionGroup},
 * {@link io.github.butterfly.security.autoconfigure.PermissionCollector}
 * 在容器刷新完成后扫描配置包并收集枚举常量, 再按
 * {@link io.github.butterfly.security.autoconfigure.PermissionStorage} 策略落库
 * ({@link io.github.butterfly.security.autoconfigure.DbPermissionStorage})或存入内存
 * ({@link io.github.butterfly.security.autoconfigure.InMemoryPermissionStorage})。
 * <p>
 * 装配入口为
 * {@link io.github.butterfly.security.autoconfigure.SecurityPermissionAutoConfiguration},
 * 配置项见 {@link io.github.butterfly.security.autoconfigure.SecurityPermissionProperties}。
 */
@NullMarked
package io.github.butterfly.security.autoconfigure;

import org.jspecify.annotations.NullMarked;
