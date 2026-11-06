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

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 授权配置,前缀 {@code butterfly.security.authorization}.
 * <p>
 * 授权回答的是「已登录用户能做什么」:登录时按用户 id 查出其角色关联的权限 id 集合, 作为 JWT 的 {@code authorities} 声明下发, 请求时由
 * Spring Security 还原为 {@code GrantedAuthority},形如 {@code hasAuthority('100')}。
 */
@Data
@ConfigurationProperties(prefix = "butterfly.security.authorization")
public class SecurityAuthorizationProperties {

	/**
	 * 授权总开关,默认开启. 关闭后不注册 {@link PermissionRepository}, 登录时下发的权限集合为空。
	 */
	private boolean enabled = true;

	/**
	 * 是否由本模块开启 Spring Security 的方法级授权,默认开启.
	 * <p>
	 * 开启后可以写 {@code @PreAuthorize("hasAuthority('100')")} 与
	 * {@link RequirePermission}。已经自己标了 {@code @EnableMethodSecurity} 的工程,重复开启是幂等的,
	 * 但也可以把本项设为 {@code false} 让本模块完全不介入。
	 */
	private boolean methodSecurityEnabled = true;

}
