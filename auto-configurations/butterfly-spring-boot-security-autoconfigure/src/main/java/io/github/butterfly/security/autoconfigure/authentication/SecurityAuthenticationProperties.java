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

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 认证配置,前缀 {@code butterfly.security.authentication}.
 * <p>
 * 认证回答的是「你是谁」:默认提供一个 JSON 登录接口,校验账号密码后签发 JWT(HS256), 之后请求带上
 * {@code Authorization: Bearer <token>} 即可通过 Spring Security 的校验。
 */
@Data
@ConfigurationProperties(prefix = "butterfly.security.authentication")
public class SecurityAuthenticationProperties {

	/**
	 * 认证总开关,默认开启. 关闭后不注册登录接口、JWT 组件与安全过滤链,完全交回使用方配置。
	 */
	private boolean enabled = true;

	/**
	 * 登录接口路径,默认 {@code /login},仅接受 {@code POST} 与 JSON 请求体.
	 * <p>
	 * 该路径会被自动放行,无需登录即可访问。
	 */
	private String loginPath = "/login";

	/**
	 * 除登录接口外无需认证的路径,默认只放行 {@code /error}.
	 */
	private List<String> permitPaths = new ArrayList<>(List.of("/error"));

	/**
	 * JWT 相关配置.
	 */
	private Jwt jwt = new Jwt();

	/**
	 * 是否在用户表为空时初始化一个默认管理员,默认开启.
	 * <p>
	 * 只在「一个用户都没有」时执行一次,已有用户时不会覆盖任何数据。
	 */
	private boolean initDefaultAdmin = true;

	/**
	 * 默认管理员的登录名.
	 */
	private String defaultAdminUsername = "admin";

	/**
	 * 默认管理员的初始密码,仅在初始化时使用.
	 * <p>
	 * 入库前会用 BCrypt 加密,数据库中不保存明文。
	 */
	private String defaultAdminPassword = "admin";

	/**
	 * JWT 签名与有效期配置.
	 */
	@Data
	public static class Jwt {

		/**
		 * HS256 签名密钥,长度至少 32 字节.
		 * <p>
		 * 为空时启动阶段会生成一个随机密钥并打印 warn 日志:单机开发够用,但重启后已签发的 token 全部失效,
		 * 多实例部署时各实例密钥不一致也会互相不认。生产环境必须显式配置。
		 */
		private String secret = "";

		/**
		 * 签发者标识,写入 {@code iss} 声明并在校验时比对.
		 */
		private String issuer = "butterfly";

		/**
		 * token 有效期,默认 2 小时.
		 */
		private Duration expiration = Duration.ofHours(2);

	}

}
