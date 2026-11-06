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

package io.github.butterfly.sandbox.security;

import io.github.butterfly.sandbox.permission.PermissionEnum;
import io.github.butterfly.security.autoconfigure.authorization.PermissionRepository;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 沙箱用的「用户 → 权限 id」实现:管理员拥有全部权限,访客只有商品列表.
 * <p>
 * 演示「框架不建角色表,授权关系由业务提供」:真实项目把这里的静态映射换成 {@code user -> user_role -> role_authority}
 * 的查询即可,返回的权限 id 就是 {@link PermissionEnum} 里的 {@code getId()},也就是 {@code authority} 表的主键。
 */
@Component
public class DemoPermissionRepository implements PermissionRepository {

	private final Map<Long, List<Long>> grants;

	/**
	 * 装配两个演示用户的权限:管理员拿全部权限,访客只拿商品列表.
	 */
	public DemoPermissionRepository() {
		List<Long> allPermissions = Arrays.stream(PermissionEnum.values()).map(PermissionEnum::getId).sorted().toList();
		this.grants = Map.of(DemoUserAccountRepository.ADMIN_ID, allPermissions, DemoUserAccountRepository.GUEST_ID,
				List.of(PermissionEnum.PRODUCT_PAGE_LIST.getId()));
	}

	/**
	 * 查询用户拥有的权限 id.
	 * @param userId 用户 id,取自 {@link DemoUserAccountRepository} 中的账号
	 * @return 权限 id 列表(升序),未登记的用户返回空列表
	 */
	@Override
	public List<Long> findPermissionIdsByUserId(long userId) {
		return this.grants.getOrDefault(userId, List.of());
	}

}
