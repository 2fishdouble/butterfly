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

import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 内存存储(无数据库环境的保底方案),同时为后续权限校验组件提供按 id 查询入口.
 */
public class InMemoryPermissionStorage implements PermissionStorage {

	private final ConcurrentMap<Long, IPermissionGroup> groups = new ConcurrentHashMap<>();

	private final ConcurrentMap<Long, IPermission> permissions = new ConcurrentHashMap<>();

	/**
	 * 存储(合并)一批权限分组与权限到内存 Map,同一 id 以新值覆盖旧值.
	 * @param groups 待存储的权限分组,可为空集合
	 * @param permissions 待存储的权限,可为空集合
	 */
	@Override
	public void store(Collection<? extends IPermissionGroup> groups, Collection<? extends IPermission> permissions) {
		for (IPermissionGroup group : groups) {
			this.groups.put(group.getId(), group);
		}
		for (IPermission permission : permissions) {
			this.permissions.put(permission.getId(), permission);
		}
	}

	/**
	 * 按 id 查询已收集的权限分组.
	 * @param id 分组 id
	 * @return 对应的分组,不存在时返回 {@code null}
	 */
	public @Nullable IPermissionGroup getGroup(long id) {
		return this.groups.get(id);
	}

	/**
	 * 按 id 查询已收集的权限.
	 * @param id 权限 id
	 * @return 对应的权限,不存在时返回 {@code null}
	 */
	public @Nullable IPermission getPermission(long id) {
		return this.permissions.get(id);
	}

	/**
	 * 已收集的全部权限分组.
	 * @return 不可修改的只读视图,内容随后续 {@link #store} 调用变化
	 */
	public Collection<IPermissionGroup> groups() {
		return Collections.unmodifiableCollection(this.groups.values());
	}

	/**
	 * 已收集的全部权限.
	 * @return 不可修改的只读视图,内容随后续 {@link #store} 调用变化
	 */
	public Collection<IPermission> permissions() {
		return Collections.unmodifiableCollection(this.permissions.values());
	}

}
