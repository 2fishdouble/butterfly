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

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * JWT 签发服务:用 HS256 对称密钥把账号与权限写进 token.
 * <p>
 * token 里的声明:
 * <ul>
 * <li>{@code sub}:登录名;</li>
 * <li>{@code uid}:用户 id,对应 {@code user.id};</li>
 * <li>{@code authorities}:权限 id 的字符串数组,请求时被还原成 {@code GrantedAuthority}, 因此
 * {@code hasAuthority('100')} 对 id 为 {@code 100} 的权限成立;</li>
 * <li>{@code iss} / {@code iat} / {@code exp}:签发者、签发时间与过期时间。</li>
 * </ul>
 * 权限是在登录时查出来写进 token 的,所以给用户改权限后需要重新登录(或在 token 过期后)才生效。
 */
public class JwtTokenService {

	/**
	 * 用户 id 声明名.
	 */
	public static final String CLAIM_USER_ID = "uid";

	/**
	 * 权限 id 声明名.
	 */
	public static final String CLAIM_AUTHORITIES = "authorities";

	private static final String TOKEN_TYPE = "Bearer";

	private final JwtEncoder jwtEncoder;

	private final String issuer;

	private final Duration expiration;

	/**
	 * 创建 JWT 签发服务.
	 * @param jwtEncoder 用于签名与编码的编码器
	 * @param properties JWT 配置,决定签发者与有效期
	 */
	public JwtTokenService(JwtEncoder jwtEncoder, SecurityAuthenticationProperties.Jwt properties) {
		this.jwtEncoder = jwtEncoder;
		this.issuer = properties.getIssuer();
		this.expiration = properties.getExpiration();
	}

	/**
	 * 为账号签发一个 JWT.
	 * @param account 已通过密码校验的账号
	 * @param permissionIds 该账号拥有的权限 id
	 * @return 紧凑格式的 JWT 字符串
	 */
	public String issue(UserAccount account, Collection<Long> permissionIds) {
		Instant issuedAt = Instant.now();
		List<String> authorities = permissionIds.stream().map(String::valueOf).toList();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(this.issuer)
			.subject(account.username())
			.issuedAt(issuedAt)
			.expiresAt(issuedAt.plus(this.expiration))
			.claim(CLAIM_USER_ID, account.id())
			.claim(CLAIM_AUTHORITIES, authorities)
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
		return this.jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	/**
	 * token 有效期(秒),登录响应里回给调用方.
	 * @return 有效期的秒数
	 */
	public long getExpiresIn() {
		return this.expiration.toSeconds();
	}

	/**
	 * token 类型,固定为 {@code Bearer}.
	 * @return token 类型
	 */
	public String getTokenType() {
		return TOKEN_TYPE;
	}

}
