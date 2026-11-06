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

import io.github.butterfly.security.autoconfigure.PermissionStorage;
import io.github.butterfly.security.autoconfigure.SecurityPermissionAutoConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.ClassFilter;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.StaticMethodMatcher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.authorization.method.AuthorizationAdvisor;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import java.lang.reflect.Method;

/**
 * 授权自动配置:提供「用户 → 权限 id」的默认查询实现,并把 {@link RequirePermission} 接入方法级授权.
 * <ul>
 * <li>默认 {@link InMemoryPermissionRepository},由使用方或默认管理员显式授予,仅适合开发与测试;</li>
 * <li>使用方自定义 {@link PermissionRepository} Bean 时直接覆盖,用于接自己的角色/权限关系表;</li>
 * <li>{@code method-security-enabled=true}(默认)时开启 Spring Security 方法级授权,
 * {@code @PreAuthorize} 与 {@link RequirePermission} 同时可用。</li>
 * </ul>
 * 由 {@code butterfly.security.authorization.enabled} 控制,缺省(未配置)时视为开启。
 */
@AutoConfiguration(after = SecurityPermissionAutoConfiguration.class)
@EnableConfigurationProperties(SecurityAuthorizationProperties.class)
@ConditionalOnProperty(prefix = "butterfly.security.authorization", name = "enabled", havingValue = "true",
		matchIfMissing = true)
public class SecurityAuthorizationAutoConfiguration {

	private static final Logger log = LoggerFactory.getLogger(SecurityAuthorizationAutoConfiguration.class);

	/**
	 * 注册权限授予关系查询实现,默认内存实现.
	 * <p>
	 * 角色与授权关系属于业务数据,框架不建表:使用方声明自己的 {@link PermissionRepository} Bean 即可接管
	 * (例如查自己的用户-角色-权限关系表),没有声明时退回内存实现并在启动时告警。
	 * @param storageProvider 权限字典存储,内存实现在授予「全部权限」时需要读它
	 * @return 权限授予关系查询实现
	 */
	@Bean
	@ConditionalOnMissingBean
	PermissionRepository permissionRepository(ObjectProvider<PermissionStorage> storageProvider) {
		log.warn("未提供 PermissionRepository, 使用内存授权实现: 重启后授权关系丢失, 生产环境请实现 PermissionRepository " + "查自己的角色/权限关系表。");
		return new InMemoryPermissionRepository(storageProvider.getIfAvailable());
	}

	/**
	 * 把 {@link RequirePermission} 接到 Spring Security 的方法拦截里.
	 * <p>
	 * 当容器开启方法级授权时,{@code AuthorizationAdvisor} 类型的 Bean 会被自动用于代理业务 Bean, 因此这里只需声明拦截器。只有
	 * classpath 上存在 Spring Security 的授权 API 时才注册。
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(AuthorizationAdvisor.class)
	static class RequirePermissionConfiguration {

		/**
		 * 注册 {@link RequirePermission} 的方法拦截器.
		 * <p>
		 * 这里不用 {@code AnnotationMatchingPointcut(类注解, 方法注解)}:那种写法要求类上必须同时带注解,
		 * 只标在方法上的服务就拦不到了。改用「类过滤器放行 + 方法匹配器同时看方法与方法所在类」,既支持方法级也支持 类级注解,并且只有存在匹配方法的 Bean
		 * 才会被代理。
		 * @return 基于注解的方法拦截器
		 */
		@Bean
		@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
		AuthorizationAdvisor requirePermissionAdvisor() {
			Pointcut pointcut = new ComposablePointcut(ClassFilter.TRUE, new RequirePermissionMethodMatcher());
			return new AuthorizationManagerBeforeMethodInterceptor(pointcut,
					new RequirePermissionAuthorizationManager());
		}

		/**
		 * 匹配标了 {@link RequirePermission} 的方法,或方法所在类标了该注解的所有方法.
		 */
		static final class RequirePermissionMethodMatcher extends StaticMethodMatcher {

			@Override
			public boolean matches(Method method, Class<?> targetClass) {
				return AnnotatedElementUtils.hasAnnotation(method, RequirePermission.class)
						|| AnnotatedElementUtils.hasAnnotation(targetClass, RequirePermission.class);
			}

		}

	}

	/**
	 * 开启 Spring Security 的方法级授权({@code @PreAuthorize} / {@link RequirePermission}).
	 * <p>
	 * 使用方如果已经自己标了 {@code @EnableMethodSecurity},把
	 * {@code butterfly.security.authorization.method-security-enabled} 设为 {@code false}
	 * 即可让本模块 完全不介入;两者都开启时注册的是同一批基础设施,不会产生重复代理。
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(EnableMethodSecurity.class)
	@ConditionalOnProperty(prefix = "butterfly.security.authorization", name = "method-security-enabled",
			havingValue = "true", matchIfMissing = true)
	@EnableMethodSecurity
	static class MethodSecurityConfiguration {

	}

}
