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

import java.util.List;

/**
 * 权限授予关系查询入口(SPI):某个登录用户拥有哪些权限 id.
 * <p>
 * 授权只认 {@code authority.id},返回值会原样变成 {@code GrantedAuthority} 的 authority 字符串
 * ({@code 100} 对应 {@code hasAuthority('100')})。角色与用户-角色关系属于业务数据,框架既不建表也不写库, 只提供内存实现
 * {@link InMemoryPermissionRepository};需要持久化时实现本接口查自己的关系表,例如:
 *
 * <pre>{@code
 * public List<Long> findPermissionIdsByUserId(long userId) {
 *     // SELECT ra.authority_id FROM sys_role_authority ra
 *     //   JOIN sys_user_role ur ON ur.role_id = ra.role_id WHERE ur.user_id = ?
 *     return loadFromMyTables(userId);
 * }
 * }</pre>
 * <p>
 * 登录时会调用一次本方法,结果写进 JWT 的 {@code authorities} 声明;因此改了用户权限后需要重新登录 (或在 token 过期后)才生效。
 */
public interface PermissionRepository {

	/**
	 * 查询用户拥有的权限 id.
	 * @param userId 用户 id,取自
	 * {@link io.github.butterfly.security.autoconfigure.authentication.UserAccount#id()}
	 * @return 权限 id 列表(建议按 id 升序),没有任何权限时返回空列表而不是 {@code null}
	 */
	List<Long> findPermissionIdsByUserId(long userId);

}
