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

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;
import java.util.function.Supplier;

/**
 * 过滤链的授权判定:已认证放行,未认证时再看目标方法有没有标 {@link AnonymousAccess}.
 * <p>
 * 为什么需要它:{@code permitAll()} / {@code @PermitAll} 这类写法只作用于方法级授权,而过滤链的
 * {@code anyRequest().authenticated()} 在进入 Controller 之前就会把匿名请求拦成 401,方法级注解根本没机会执行。
 * 所以这里把第二步「这个方法是否允许匿名」提前到过滤链里判断。
 * <p>
 * 判定顺序刻意把便宜的分支放前面:先看当前是否已认证(绝大多数带 token 的请求走这里,不碰 MVC), 只有匿名请求才会去问
 * {@link RequestMappingHandlerMapping} 「这个请求会落到哪个方法」。
 */
public final class AnonymousAccessAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

	private static final Logger log = LoggerFactory.getLogger(AnonymousAccessAuthorizationManager.class);

	private final List<RequestMappingHandlerMapping> handlerMappings;

	/**
	 * 创建判定器.
	 * @param handlerMappings MVC 的映射器,用于把请求解析成目标方法;为空时退化为「只认登录状态」
	 */
	public AnonymousAccessAuthorizationManager(List<RequestMappingHandlerMapping> handlerMappings) {
		this.handlerMappings = List.copyOf(handlerMappings);
	}

	/**
	 * 判定当前请求是否放行.
	 * @param authentication 当前认证信息的提供者;未登录时取值为 {@code null} 或匿名认证
	 * @param context 当前请求的授权上下文
	 * @return 判定结果,不会为 {@code null}:已认证放行;匿名时仅放行 {@link AnonymousAccess} 标注的接口
	 */
	@Override
	public AuthorizationResult authorize(Supplier<? extends @Nullable Authentication> authentication,
			RequestAuthorizationContext context) {
		return decide(authentication.get(), context.getRequest());
	}

	private AuthorizationResult decide(@Nullable Authentication current, HttpServletRequest request) {
		if (isAuthenticated(current)) {
			return new AuthorizationDecision(true);
		}
		return new AuthorizationDecision(isAnonymousAllowed(request));
	}

	private static boolean isAuthenticated(@Nullable Authentication current) {
		return current != null && current.isAuthenticated() && !(current instanceof AnonymousAuthenticationToken);
	}

	/**
	 * 问 MVC「这个请求会落到哪个方法」,再看方法或它所在的类有没有 {@link AnonymousAccess}.
	 * @param request 当前请求
	 * @return 目标方法允许匿名访问时返回 {@code true};解析不出方法(静态资源、未映射路径)时为 {@code false}
	 */
	private boolean isAnonymousAllowed(HttpServletRequest request) {
		for (RequestMappingHandlerMapping handlerMapping : this.handlerMappings) {
			try {
				if (isAnonymousHandler(handlerMapping.getHandler(request))) {
					return true;
				}
			}
			catch (Exception ex) {
				// 方法不支持、路径不匹配等,都按「没有匿名许可」处理,交给上层返回 401
				if (log.isDebugEnabled()) {
					log.debug("解析请求对应的 HandlerMethod 失败, uri={}", request.getRequestURI(), ex);
				}
				return false;
			}
		}
		return false;
	}

	/**
	 * 判断命中的 handler 是不是允许匿名访问的 Controller 方法.
	 * <p>
	 * {@link RequestMappingHandlerMapping#getHandler} 允许返回 {@code null}(没有匹配的方法),所以把它作为可空
	 * 参数交给本方法处理,方法体里不出现可空局部变量。
	 * @param chain MVC 解析出的执行链,可能为 {@code null}
	 * @return handler 是标了 {@link AnonymousAccess} 的 {@code HandlerMethod} 时返回
	 * {@code true}
	 */
	private static boolean isAnonymousHandler(@Nullable HandlerExecutionChain chain) {
		if (chain == null) {
			return false;
		}
		if (chain.getHandler() instanceof HandlerMethod handlerMethod) {
			return hasAnonymousAccess(handlerMethod);
		}
		return false;
	}

	private static boolean hasAnonymousAccess(HandlerMethod handlerMethod) {
		return AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), AnonymousAccess.class)
				|| AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), AnonymousAccess.class);
	}

}
