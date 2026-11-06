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
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JWT 签发服务的单元测试:声明的取值、有效期与 token 类型.
 */
class JwtTokenServiceTests {

	private static final String SECRET = "butterfly-test-secret-0123456789abcdef";

	private final SecretKey secretKey = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

	@Test
	void issuesTokenWithExpectedClaims() {
		JwtTokenService jwtTokenService = jwtTokenService(Duration.ofMinutes(30));
		UserAccount account = new UserAccount(7L, "alice", "bcrypt-hash", "Alice", true);

		String token = jwtTokenService.issue(account, List.of(200L, 100L));

		Jwt decoded = decoder().decode(token);
		assertThat(decoded.getSubject()).isEqualTo("alice");
		assertThat(decoded.<Long>getClaim(JwtTokenService.CLAIM_USER_ID)).isEqualTo(7L);
		assertThat(decoded.getClaimAsStringList(JwtTokenService.CLAIM_AUTHORITIES)).containsExactly("200", "100");
		assertThat(decoded.getClaimAsString("iss")).isEqualTo("butterfly-test");
		assertThat(decoded.getExpiresAt()).isAfter(decoded.getIssuedAt());
		assertThat(jwtTokenService.getExpiresIn()).isEqualTo(1800L);
		assertThat(jwtTokenService.getTokenType()).isEqualTo("Bearer");
	}

	@Test
	void issuesTokenWithoutPermissions() {
		JwtTokenService jwtTokenService = jwtTokenService(Duration.ofHours(1));

		String token = jwtTokenService.issue(new UserAccount(8L, "bob", "bcrypt-hash", null, true), List.of());

		assertThat(decoder().decode(token).getClaimAsStringList(JwtTokenService.CLAIM_AUTHORITIES)).isEmpty();
	}

	private JwtTokenService jwtTokenService(Duration expiration) {
		SecurityAuthenticationProperties.Jwt properties = new SecurityAuthenticationProperties.Jwt();
		properties.setIssuer("butterfly-test");
		properties.setExpiration(expiration);
		return new JwtTokenService(new NimbusJwtEncoder(new ImmutableSecret<>(this.secretKey)), properties);
	}

	private NimbusJwtDecoder decoder() {
		return NimbusJwtDecoder.withSecretKey(this.secretKey).macAlgorithm(MacAlgorithm.HS256).build();
	}

}
