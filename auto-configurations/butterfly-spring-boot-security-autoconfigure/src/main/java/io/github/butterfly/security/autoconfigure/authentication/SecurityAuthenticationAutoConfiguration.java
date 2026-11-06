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

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import io.github.butterfly.security.autoconfigure.authorization.PermissionRepository;
import io.github.butterfly.security.autoconfigure.authorization.RequirePermission;
import io.github.butterfly.security.autoconfigure.authorization.SecurityAuthorizationAutoConfiguration;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 认证自动配置:账号读取、BCrypt 密码编码、HS256 的 JWT 签发与校验、登录接口,以及一条无状态的安全过滤链.
 * <ul>
 * <li>账号默认来自 {@link InMemoryUserAccountRepository};使用方声明自己的 {@link UserAccountRepository}
 * Bean 即可接管,框架不会向业务实现写入任何数据;</li>
 * <li>登录接口默认 {@code POST /login},可用 {@code login-path} 改路径,其余路径一律要求认证;</li>
 * <li>未登录返回 401、权限不足返回 403,响应体都是统一结构 {@code R}。</li>
 * </ul>
 * 由 {@code butterfly.security.authentication.enabled} 控制,缺省(未配置)时视为开启。
 * <p>
 * 本类只在 classpath 上有 Spring Security 且引入 JWT 实现时生效(相关依赖都是 optional), 未引入时整个认证能力
 * 静默不装配,权限收集照常工作。过滤链声明在 {@link ServletWebSecurityAutoConfiguration} 之前,因此会自动顶替 Spring
 * Boot 默认的 Basic 认证。
 */
@AutoConfiguration(before = ServletWebSecurityAutoConfiguration.class,
		after = SecurityAuthorizationAutoConfiguration.class)
@EnableConfigurationProperties(SecurityAuthenticationProperties.class)
@ConditionalOnClass({ SecurityFilterChain.class, JwtDecoder.class, JwtAuthenticationConverter.class })
@ConditionalOnProperty(prefix = "butterfly.security.authentication", name = "enabled", havingValue = "true",
		matchIfMissing = true)
public class SecurityAuthenticationAutoConfiguration {

	/**
	 * HS256 密钥的最小长度(字节).
	 */
	private static final int MIN_SECRET_LENGTH = 32;

	private static final Logger log = LoggerFactory.getLogger(SecurityAuthenticationAutoConfiguration.class);

	/**
	 * 注册账号读取实现,默认内存实现.
	 * <p>
	 * 账号属于业务数据,框架不建表:使用方声明自己的 {@link UserAccountRepository} Bean 即可接管(查自己的用户表、 走 LDAP
	 * 或外部单点登录),没有声明时退回内存实现并在启动时告警。
	 * @return 账号读取实现
	 */
	@Bean
	@ConditionalOnMissingBean
	UserAccountRepository userAccountRepository() {
		log.warn("未提供 UserAccountRepository, 使用内存账号实现(重启后账号丢失), 生产环境请自行实现该接口。");
		return new InMemoryUserAccountRepository();
	}

	/**
	 * 注册密码编码器,默认 BCrypt.
	 * @return 使用 BCrypt 的密码编码器
	 */
	@Bean
	@ConditionalOnMissingBean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	/**
	 * 提供 HS256 签名密钥:配置了 {@code jwt.secret} 就用它,否则本次启动生成随机密钥并告警.
	 * @param properties 认证配置
	 * @return HMAC-SHA256 密钥
	 * @throws IllegalStateException 配置的密钥短于 32 字节时抛出,避免用弱密钥签名
	 */
	@Bean
	@ConditionalOnMissingBean
	SecretKey jwtSecretKey(SecurityAuthenticationProperties properties) {
		return createSecretKey(properties.getJwt().getSecret());
	}

	/**
	 * 生成 HS256 密钥:配置了就用它,没配(或为空)时随机生成并告警.
	 * @param configuredSecret 配置的密钥,可能为 {@code null} 或空白
	 * @return HMAC-SHA256 密钥
	 * @throws IllegalStateException 配置的密钥短于 32 字节时抛出,避免用弱密钥签名
	 */
	private static SecretKey createSecretKey(@Nullable String configuredSecret) {
		// 配置绑定可能给出 null 或空白串(例如 secret: 后面什么都不写),两种都按「未配置」处理
		if (configuredSecret == null || configuredSecret.isBlank()) {
			log.warn("未配置 butterfly.security.authentication.jwt.secret, 本次启动使用随机密钥: "
					+ "重启后已签发的 token 全部失效, 多实例部署时各实例也互不认可, 生产环境请显式配置。");
			byte[] randomBytes = new byte[MIN_SECRET_LENGTH];
			new SecureRandom().nextBytes(randomBytes);
			return new SecretKeySpec(randomBytes, "HmacSHA256");
		}
		byte[] secretBytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
		if (secretBytes.length < MIN_SECRET_LENGTH) {
			throw new IllegalStateException("butterfly.security.authentication.jwt.secret 至少需要 " + MIN_SECRET_LENGTH
					+ " 字节(UTF-8), 当前为 " + secretBytes.length + " 字节。");
		}
		return new SecretKeySpec(secretBytes, "HmacSHA256");
	}

	/**
	 * 注册 JWT 编码器(HS256).
	 * @param jwtSecretKey 签名密钥
	 * @return 基于对称密钥的编码器
	 */
	@Bean
	@ConditionalOnMissingBean
	JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
	}

	/**
	 * 注册 JWT 解码器(HS256),并校验签发者与过期时间.
	 * @param jwtSecretKey 签名密钥
	 * @param properties 认证配置,提供签发者
	 * @return JWT 解码器
	 */
	@Bean
	@ConditionalOnMissingBean
	JwtDecoder jwtDecoder(SecretKey jwtSecretKey, SecurityAuthenticationProperties properties) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
			.macAlgorithm(MacAlgorithm.HS256)
			.build();
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.getJwt().getIssuer()));
		return decoder;
	}

	/**
	 * 注册 JWT 签发服务.
	 * @param jwtEncoder JWT 编码器
	 * @param properties 认证配置,提供签发者与有效期
	 * @return JWT 签发服务
	 */
	@Bean
	@ConditionalOnMissingBean
	JwtTokenService jwtTokenService(JwtEncoder jwtEncoder, SecurityAuthenticationProperties properties) {
		return new JwtTokenService(jwtEncoder, properties.getJwt());
	}

	/**
	 * 注册登录流程.
	 * @param userAccounts 账号读取入口
	 * @param passwordEncoder 密码编码器
	 * @param jwtTokenService JWT 签发服务
	 * @param permissionRepositories 权限授予关系实现,授权关闭时为空
	 * @return 登录流程
	 */
	@Bean
	@ConditionalOnMissingBean
	LoginService loginService(UserAccountRepository userAccounts, PasswordEncoder passwordEncoder,
			JwtTokenService jwtTokenService, ObjectProvider<PermissionRepository> permissionRepositories) {
		return new LoginService(userAccounts, passwordEncoder, jwtTokenService,
				permissionRepositories.getIfAvailable());
	}

	/**
	 * 注册默认管理员初始化器,只在账号后端是内存实现且没有任何账号时建号.
	 * @param userAccounts 账号读取入口
	 * @param passwordEncoder 密码编码器
	 * @param permissionRepositories 权限授予关系实现,内存实现会被直接授予全部权限
	 * @param properties 认证配置,提供默认管理员的登录名与初始密码
	 * @return 默认管理员初始化器
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = "butterfly.security.authentication", name = "init-default-admin",
			havingValue = "true", matchIfMissing = true)
	DefaultAdminInitializer defaultAdminInitializer(UserAccountRepository userAccounts, PasswordEncoder passwordEncoder,
			ObjectProvider<PermissionRepository> permissionRepositories, SecurityAuthenticationProperties properties) {
		return new DefaultAdminInitializer(userAccounts, passwordEncoder, permissionRepositories.getIfAvailable(),
				properties);
	}

	/**
	 * Web 相关 Bean:只在 Servlet Web 应用里注册,且要求 classpath 上有 Spring MVC.
	 * <p>
	 * 与 {@code ButterflyAutoConfiguration.WebConfiguration} 一样放进嵌套配置类:条件只读注解元数据, 不加载 Web
	 * 类型,非 Web 应用(或没有 spring-web)时整块跳过。
	 */
	@SuppressWarnings("BadImport")
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnWebApplication(type = Type.SERVLET)
	@ConditionalOnClass({ SecurityFilterChain.class, RestController.class, RequestMappingHandlerMapping.class })
	static class WebSecurityConfiguration {

		/**
		 * 注册登录接口.
		 * @param loginService 登录流程
		 * @return 登录接口
		 */
		@Bean
		@ConditionalOnMissingBean
		LoginController loginController(LoginService loginService) {
			return new LoginController(loginService);
		}

		/**
		 * 注册 401/403 的统一响应处理器.
		 * <p>
		 * 优先复用容器里的 {@link ObjectMapper},让错误响应与业务响应共用同一套序列化规则; 没有 Jackson 自动配置时 退化为默认的
		 * {@link JsonMapper},保证过滤链仍能启动。
		 * @param objectMapperProvider 容器中的 Jackson 映射器
		 * @return 认证/授权异常处理器
		 */
		@Bean
		@ConditionalOnMissingBean
		JsonSecurityExceptionHandler jsonSecurityExceptionHandler(ObjectProvider<ObjectMapper> objectMapperProvider) {
			ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(JsonMapper::new);
			return new JsonSecurityExceptionHandler(objectMapper);
		}

		/**
		 * 注册方法级授权的异常映射,把 {@code @PreAuthorize} / {@link RequirePermission} 的拒绝也变成
		 * 403/401.
		 * <p>
		 * 这类异常发生在 Controller 方法内部,过滤链的 {@code AccessDeniedHandler} 收不到,只能靠 MVC
		 * 的异常处理器;没有它就会被全局兜底变成「HTTP 200 + Access Denied」。
		 * @return 方法级授权异常处理器
		 */
		@Bean
		@ConditionalOnMissingBean
		WebSecurityExceptionAdvice webSecurityExceptionAdvice() {
			return new WebSecurityExceptionAdvice();
		}

		/**
		 * 注册 JWT 到权限的转换器:把 token 中的 {@code authorities} 声明原样变成 {@code GrantedAuthority}.
		 * <p>
		 * 声明里存的是权限 id 字符串,因此不加任何前缀,{@code hasAuthority('100')} 直接可用。
		 * @return JWT 认证转换器
		 */
		@Bean
		@ConditionalOnMissingBean(name = "jwtAuthenticationConverter")
		Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
			JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
			authoritiesConverter.setAuthoritiesClaimName(JwtTokenService.CLAIM_AUTHORITIES);
			authoritiesConverter.setAuthorityPrefix("");
			JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
			converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
			return converter;
		}

		/**
		 * 注册用户上下文过滤器:把 JWT 里的用户信息放进 {@link UserContext},请求结束时清理.
		 * @return 用户上下文过滤器
		 */
		@Bean
		@ConditionalOnMissingBean
		UserContextFilter userContextFilter() {
			return new UserContextFilter();
		}

		/**
		 * 注册上下文传递装饰器,给异步执行器用:挂到 {@code ThreadPoolTaskExecutor} 上, {@code @Async} 方法里也能读到
		 * {@link UserContext}.
		 * <p>
		 * 只是一个可用的 Bean,不会自动套到任何执行器上——是否传播到某个线程池,由业务自己决定。
		 * @return 任务装饰器
		 */
		@Bean
		@ConditionalOnMissingBean
		UserContextTaskDecorator userContextTaskDecorator() {
			return new UserContextTaskDecorator();
		}

		/**
		 * 注册过滤链的授权判定:已认证放行,匿名时放行 {@link AnonymousAccess} 标注的接口.
		 * <p>
		 * 需要 MVC 的 {@link RequestMappingHandlerMapping} 才能把请求解析成目标方法;容器里没有时退化为
		 * 「只认登录状态」,等价于原来的 {@code anyRequest().authenticated()}。
		 * @param handlerMappings MVC 的请求映射器
		 * @return 匿名访问判定器
		 */
		@Bean
		@ConditionalOnMissingBean
		AnonymousAccessAuthorizationManager anonymousAccessAuthorizationManager(
				ObjectProvider<RequestMappingHandlerMapping> handlerMappings) {
			return new AnonymousAccessAuthorizationManager(handlerMappings.orderedStream().toList());
		}

		/**
		 * 注册无状态的安全过滤链:登录路径放行,其余请求要求携带合法 JWT.
		 * <p>
		 * 关闭 CSRF(纯 token 的无状态接口不需要)、关闭 session 与表单登录;未登录与权限不足都返回统一 JSON 响应。 使用方自己声明
		 * {@link SecurityFilterChain} 时本方法不生效。
		 * @param http 用于构建过滤链的 Spring Security HTTP 配置入口
		 * @param properties 认证配置,提供登录路径与放行路径
		 * @param jwtAuthenticationConverter JWT 认证转换器
		 * @param exceptionHandler 401/403 的统一响应处理器
		 * @param anonymousAccessAuthorizationManager 未认证请求的判定器,负责 {@link AnonymousAccess}
		 * @param userContextFilter 用户上下文过滤器,认证完成后把用户信息放进 {@link UserContext}
		 * @return 安全过滤链
		 */
		@Bean
		@ConditionalOnMissingBean(SecurityFilterChain.class)
		SecurityFilterChain butterflySecurityFilterChain(HttpSecurity http, SecurityAuthenticationProperties properties,
				Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter,
				JsonSecurityExceptionHandler exceptionHandler,
				AnonymousAccessAuthorizationManager anonymousAccessAuthorizationManager,
				UserContextFilter userContextFilter) {
			http.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement((session) -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(
						(authorize) -> authorize.requestMatchers(HttpMethod.POST, properties.getLoginPath())
							.permitAll()
							.requestMatchers(permitPaths(properties))
							.permitAll()
							.anyRequest()
							.access(anonymousAccessAuthorizationManager))
				.oauth2ResourceServer((resourceServer) -> resourceServer.authenticationEntryPoint(exceptionHandler)
					.jwt((jwt) -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
				// 必须排在 BearerTokenAuthenticationFilter 之后,才能在认证完成后读到 JWT
				.addFilterAfter(userContextFilter, BearerTokenAuthenticationFilter.class)
				.exceptionHandling((exceptions) -> exceptions.authenticationEntryPoint(exceptionHandler)
					.accessDeniedHandler(exceptionHandler))
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable);
			return http.build();
		}

		private static String[] permitPaths(SecurityAuthenticationProperties properties) {
			Set<String> paths = new LinkedHashSet<>(properties.getPermitPaths());
			paths.add(properties.getLoginPath());
			return paths.toArray(String[]::new);
		}

	}

}
