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

import java.util.Set;

/**
 * 当前登录用户:从 JWT 里解析出来的、业务最常用的三样信息.
 * <p>
 * 由 {@link UserContextFilter} 在请求进入时放进 {@link UserContext},业务代码在任意位置用
 * {@code UserContext.require()} 取即可,不需要再在方法参数里传 {@code Authentication}。
 *
 * @param userId 用户 id,取自 token 的 {@code uid} 声明
 * @param username 登录名,取自 token 的 {@code sub} 声明
 * @param permissionIds 权限 id 集合,取自 token 的 {@code authorities} 声明
 */
public record LoginUser(long userId, String username, Set<Long> permissionIds) {

	/**
	 * 紧凑构造器:对权限集合做一次只读拷贝,避免调用方后续改动影响上下文.
	 */
	public LoginUser {
		permissionIds = Set.copyOf(permissionIds);
	}

}
