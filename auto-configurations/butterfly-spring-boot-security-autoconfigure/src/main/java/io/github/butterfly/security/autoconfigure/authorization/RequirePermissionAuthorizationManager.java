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

import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.function.Supplier;

/**
 * {@link RequirePermission} 的判定逻辑:把注解里要求的权限 id 与当前登录用户的 {@code GrantedAuthority}
 * 逐个比对,全部命中才放行.
 * <p>
 * 权限 id 以字符串形式存放在 {@code GrantedAuthority} 中(登录时由 JWT 的 {@code authorities} 声明还原),所以
 * {@code 100} 对应 {@code hasAuthority('100')}。
 * <p>
 * 判定永不返回 {@code null}:Spring Security 允许认证信息的提供者为空(未登录、匿名),这里一律判定为拒绝,
 * 让调用方走「认证失败」而不是「无法判定」的分支;方法或类上没有注解时判定为放行。
 */
public final class RequirePermissionAuthorizationManager implements AuthorizationManager<MethodInvocation> {

	/**
	 * 判定当前调用是否满足 {@link RequirePermission}.
	 * @param authentication 当前认证信息的提供者,由 Spring Security 传入;其取值可能为 {@code null}(未登录)
	 * @param invocation 被拦截的方法调用
	 * @return 判定结果,不会为 {@code null};方法或类上找不到 {@link RequirePermission} 时视为放行
	 */
	@Override
	public AuthorizationResult authorize(Supplier<? extends @Nullable Authentication> authentication,
			MethodInvocation invocation) {
		return decide(authentication.get(), findAnnotation(invocation));
	}

	/**
	 * 真正的判定:可空值只从参数进来,在这里一次处理干净.
	 * @param current 当前认证信息,未登录时为 {@code null}
	 * @param required 方法或类上的注解,没有注解时为 {@code null}
	 * @return 判定结果,不会为 {@code null}
	 */
	private static AuthorizationResult decide(@Nullable Authentication current, @Nullable RequirePermission required) {
		if (required == null) {
			return new AuthorizationDecision(true);
		}
		if (current == null || !current.isAuthenticated() || current instanceof AnonymousAuthenticationToken) {
			return new AuthorizationDecision(false);
		}
		return new AuthorizationDecision(allGranted(required, current.getAuthorities()));
	}

	private static boolean allGranted(RequirePermission required, Collection<? extends GrantedAuthority> granted) {
		for (long permissionId : required.value()) {
			if (!hasAuthority(granted, Long.toString(permissionId))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * 判断授予的权限里是否包含指定 authority.
	 * <p>
	 * {@link GrantedAuthority#getAuthority()} 在 Spring Security 里是可以返回 {@code null}
	 * 的,所以这里不把 authority 收进 {@code Set<String>}(那会得到
	 * {@code Set<@Nullable String>}),而是逐个比较: 为 {@code null} 的 authority 自然不会等于任何权限 id。
	 * @param granted 当前用户的权限集合
	 * @param authority 期望的 authority,即权限 id 的字符串形式
	 * @return 命中时返回 {@code true}
	 */
	private static boolean hasAuthority(Collection<? extends GrantedAuthority> granted, String authority) {
		for (GrantedAuthority candidate : granted) {
			if (authority.equals(candidate.getAuthority())) {
				return true;
			}
		}
		return false;
	}

	private static @Nullable RequirePermission findAnnotation(MethodInvocation invocation) {
		Method method = invocation.getMethod();
		return firstPresent(AnnotatedElementUtils.findMergedAnnotation(method, RequirePermission.class),
				AnnotatedElementUtils.findMergedAnnotation(method.getDeclaringClass(), RequirePermission.class));
	}

	private static @Nullable RequirePermission firstPresent(@Nullable RequirePermission first,
			@Nullable RequirePermission second) {
		return (first != null) ? first : second;
	}

}
