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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 允许匿名访问:标在 Controller 方法或类上,该接口不再要求携带 token.
 *
 * <pre>{@code
 * &#64;AnonymousAccess
 * &#64;GetMapping("/api/health")
 * public R<String> health() {
 *     return R.success("ok");
 * }
 * }</pre>
 * <p>
 * 标在类上时,该类所有接口都允许匿名访问。
 * <p>
 * 与 {@code butterfly.security.authentication.permit-paths} 的区别:
 * <ul>
 * <li>{@code permit-paths} 按 <b>路径</b> 放行,适合 {@code /error}、静态资源这类没有对应 Controller
 * 方法的位置;</li>
 * <li>本注解按 <b>方法</b> 放行,路径改了注解跟着方法走,不需要同步维护路径配置;类上标一次即可覆盖整个 Controller。</li>
 * </ul>
 * <p>
 * 注意:本注解只免除「必须登录」这一层,<b>不会</b>绕过方法级授权。如果方法上还有 {@code @PreAuthorize} 或
 * {@link io.github.butterfly.security.autoconfigure.authorization.RequirePermission},
 * 它们仍会生效(匿名用户会被拒绝);需要匿名访问的方法不要再标权限注解。
 * <p>
 * 生效依赖 {@link AnonymousAccessAuthorizationManager}:它会在过滤链判定「未登录」之前,先问 Spring MVC
 * 这个请求最终会落到哪个方法,再决定是否放行。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface AnonymousAccess {

}
