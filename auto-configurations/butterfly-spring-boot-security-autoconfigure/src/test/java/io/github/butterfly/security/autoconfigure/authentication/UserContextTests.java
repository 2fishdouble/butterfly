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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link UserContext} 的读写与清理.
 */
class UserContextTests {

	@AfterEach
	void clearContext() {
		UserContext.clear();
	}

	@Test
	void storesAndExposesCurrentUser() {
		UserContext.set(new LoginUser(7L, "alice", Set.of(100L, 101L)));

		assertThat(UserContext.isPresent()).isTrue();
		assertThat(UserContext.userId()).isEqualTo(7L);
		assertThat(UserContext.username()).isEqualTo("alice");
		assertThat(UserContext.permissionIds()).containsExactlyInAnyOrder(100L, 101L);
	}

	@Test
	void isEmptyBeforeSetAndAfterClear() {
		assertThat(UserContext.isPresent()).isFalse();
		assertThat(UserContext.get()).isNull();

		UserContext.set(new LoginUser(1L, "admin", Set.of()));
		assertThat(UserContext.isPresent()).isTrue();

		UserContext.clear();
		assertThat(UserContext.isPresent()).isFalse();
	}

	@Test
	void requireFailsWhenNoUser() {
		assertThatThrownBy(UserContext::require).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("当前线程没有登录用户");
	}

	@Test
	void permissionIdsAreDefensiveCopy() {
		Set<Long> permissions = new HashSet<>(Set.of(100L));
		UserContext.set(new LoginUser(1L, "admin", permissions));

		permissions.add(200L);

		assertThat(UserContext.permissionIds()).containsExactly(100L);
	}

}
