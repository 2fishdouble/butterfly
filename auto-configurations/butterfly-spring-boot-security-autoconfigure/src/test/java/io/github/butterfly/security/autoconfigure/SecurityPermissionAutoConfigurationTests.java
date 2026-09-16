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

package io.github.butterfly.security.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityPermissionAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(SecurityPermissionAutoConfiguration.class))
		.withPropertyValues("butterfly.security.permission.scan-packages=" + SamplePermission.class.getPackageName());

	@Test
	void noDataSourceFallsBackToInMemoryAndCollectsEnums() {
		this.contextRunner.run((context) -> {
			PermissionStorage storage = context.getBean(PermissionStorage.class);
			assertThat(storage).isInstanceOf(InMemoryPermissionStorage.class);

			InMemoryPermissionStorage memory = (InMemoryPermissionStorage) storage;
			assertThat(memory.getGroup(1L)).isEqualTo(SamplePermissionGroup.SYSTEM);
			assertThat(memory.getGroup(2L)).isEqualTo(SamplePermissionGroup.SALE);
			assertThat(memory.getPermission(100L)).isEqualTo(SamplePermission.SYSTEM_PAGE);
			assertThat(memory.getPermission(101L)).isEqualTo(SamplePermission.SYSTEM_USER_ADD);
			assertThat(memory.getPermission(201L)).isEqualTo(SamplePermission.SALE_EXPORT);
			assertThat(memory.permissions()).hasSize(SamplePermission.values().length);
			assertThat(memory.groups()).hasSize(SamplePermissionGroup.values().length);
		});
	}

	@Test
	void disabledSkipsStorageAndCollector() {
		this.contextRunner.withPropertyValues("butterfly.security.permission.enabled=false").run((context) -> {
			assertThat(context).doesNotHaveBean(PermissionStorage.class);
			assertThat(context).doesNotHaveBean(PermissionCollector.class);
		});
	}

}
