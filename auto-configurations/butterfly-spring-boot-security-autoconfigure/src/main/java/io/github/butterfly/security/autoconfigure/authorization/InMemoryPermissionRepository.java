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

package io.github.butterfly.security.autoconfigure.authorization;

import io.github.butterfly.security.autoconfigure.IPermission;
import io.github.butterfly.security.autoconfigure.InMemoryPermissionStorage;
import io.github.butterfly.security.autoconfigure.PermissionStorage;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 内存授权实现(无数据库时的保底方案).
 * <p>
 * 直接维护「用户 id → 权限 id 集合」的映射,由使用方或默认管理员初始化调用 {@link #grant} / {@link #grantAll}
 * 授予权限。适合本地开发与测试,进程重启后授予关系丢失;需要持久化时请实现 {@link PermissionRepository} 查自己的角色/授权关系表。
 * <p>
 * 本实现是线程安全的:读取与授予都基于 {@link ConcurrentHashMap}。
 */
public class InMemoryPermissionRepository implements PermissionRepository {

	private static final Logger log = LoggerFactory.getLogger(InMemoryPermissionRepository.class);

	private final @Nullable PermissionStorage permissionStorage;

	private final ConcurrentMap<Long, Set<Long>> grants = new ConcurrentHashMap<>();

	/**
	 * 创建内存授权实现.
	 * @param permissionStorage 权限字典存储,仅在 {@link #grantAll} 需要知道「全部权限」时使用; 为 {@code null}
	 * 或不是内存存储时,{@link #grantAll} 只能授予空集合
	 */
	public InMemoryPermissionRepository(@Nullable PermissionStorage permissionStorage) {
		this.permissionStorage = permissionStorage;
	}

	/**
	 * 授予某个用户若干权限 id,重复授予是幂等的.
	 * @param userId 用户 id
	 * @param permissionIds 权限 id,可传多个
	 */
	public void grant(long userId, long... permissionIds) {
		Set<Long> granted = this.grants.computeIfAbsent(userId, (key) -> new TreeSet<>());
		Arrays.stream(permissionIds).forEach(granted::add);
	}

	/**
	 * 撤销某个用户的若干权限 id,撤销不存在的权限不会报错.
	 * @param userId 用户 id
	 * @param permissionIds 权限 id,可传多个
	 */
	public void revoke(long userId, long... permissionIds) {
		this.grants.computeIfPresent(userId, (key, granted) -> {
			Arrays.stream(permissionIds).forEach(granted::remove);
			return granted;
		});
	}

	/**
	 * 把当前已收集的全部权限授予某个用户,默认管理员初始化时调用.
	 * <p>
	 * 「全部权限」来自 {@link InMemoryPermissionStorage} 中收集到的权限枚举;权限字典走了数据库存储时这里 拿不到,会记录一条 warn
	 * 日志并授予空集合。
	 * @param userId 用户 id
	 */
	public void grantAll(long userId) {
		Set<Long> all = allCollectedPermissionIds();
		if (all.isEmpty()) {
			log.warn("没有可授予的权限: 内存授权只认识 InMemoryPermissionStorage 中收集到的权限, 当前存储为 {}.", storageType());
		}
		this.grants.put(userId, all);
	}

	/**
	 * 查询用户被授予的权限 id,返回的是快照,后续授予不影响已返回的列表.
	 * @param userId 用户 id
	 * @return 权限 id 列表(按 id 升序),未授予任何权限时返回空列表
	 */
	@Override
	public List<Long> findPermissionIdsByUserId(long userId) {
		return copyOf(this.grants.get(userId));
	}

	private static List<Long> copyOf(@Nullable Set<Long> granted) {
		if (granted == null) {
			return List.of();
		}
		return new ArrayList<>(granted);
	}

	private Set<Long> allCollectedPermissionIds() {
		Set<Long> ids = new TreeSet<>();
		if (this.permissionStorage instanceof InMemoryPermissionStorage memory) {
			memory.permissions().stream().map(IPermission::getId).forEach(ids::add);
		}
		return ids;
	}

	private String storageType() {
		return (this.permissionStorage != null) ? this.permissionStorage.getClass().getSimpleName() : "none";
	}

}
