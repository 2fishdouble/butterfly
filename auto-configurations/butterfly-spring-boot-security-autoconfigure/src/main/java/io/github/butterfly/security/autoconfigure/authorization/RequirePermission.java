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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法级授权注解:当前登录用户必须拥有列出的全部权限 id 才能执行.
 * <p>
 * 标在方法上表示调用前需要这些权限,例如在 {@code create} 方法上写 {@code @RequirePermission(101L)};需要同时满足多个时写成
 * {@code @RequirePermission({101L, 102L})}。
 * <p>
 * 语义是「全部满足」({@code AND});需要「任一满足」时直接用 Spring Security 的表达式:
 * {@code @PreAuthorize("hasAuthority('100') or hasAuthority('101')")}。
 * <p>
 * 可以标在方法上,也可以标在类上(类上等同于该类的所有方法都带这个要求)。注解由 {@link RequirePermissionAuthorizationManager}
 * 读取并校验,依赖方法级授权被开启,即
 * {@code butterfly.security.authorization.method-security-enabled=true}(默认开启)。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface RequirePermission {

	/**
	 * 必需的权限 id,对应使用方权限枚举的 {@code getId()},即 {@code authority.id}.
	 * @return 权限 id,多个时要求全部满足
	 */
	long[] value();

}
