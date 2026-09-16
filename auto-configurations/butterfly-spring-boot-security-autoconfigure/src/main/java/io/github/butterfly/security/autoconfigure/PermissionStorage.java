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

package io.github.butterfly.security.autoconfigure;

import java.util.Collection;

/**
 * 权限收集结果的落库策略(SPI).
 * <ul>
 * <li>无数据库环境 → {@link InMemoryPermissionStorage} 保底;</li>
 * <li>有 {@code DataSource} → {@link DbPermissionStorage} 自动建表入库;</li>
 * <li>使用方也可自定义实现并声明为 {@code @Bean},将覆盖框架默认存储。</li>
 * </ul>
 */
public interface PermissionStorage {

	/**
	 * 存储(合并)一批权限分组与权限.同一 id 以新值覆盖旧值.
	 * @param groups 待存储的权限分组,可为空集合
	 * @param permissions 待存储的权限,可为空集合
	 */
	void store(Collection<? extends IPermissionGroup> groups, Collection<? extends IPermission> permissions);

}
