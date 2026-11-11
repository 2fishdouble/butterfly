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

package io.github.butterfly.file.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.util.unit.DataSize;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 文件自动配置的 Bean 注册与让位条件:有数据源才装配存储,自定义 Bean 时让位,Web 应用才注册接口.
 */
class FileAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(FileAutoConfiguration.class, FileWebAutoConfiguration.class));

	private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(FileAutoConfiguration.class, FileWebAutoConfiguration.class));

	@Test
	void registersStorageAndServiceWhenDataSourcePresent() {
		this.contextRunner.withBean(DataSource.class, () -> mock(DataSource.class)).run((context) -> {
			assertThat(context).hasSingleBean(FileStorage.class);
			assertThat(context.getBean(FileStorage.class)).isInstanceOf(MysqlFileStorage.class);
			assertThat(context).hasSingleBean(FileService.class);
		});
	}

	@Test
	void skipsEverythingWithoutDataSource() {
		this.contextRunner.run((context) -> {
			assertThat(context).doesNotHaveBean(FileStorage.class);
			assertThat(context).doesNotHaveBean(FileService.class);
			assertThat(context).doesNotHaveBean(FileController.class);
		});
	}

	@Test
	void skipsEverythingWhenDisabled() {
		this.webContextRunner.withPropertyValues("butterfly.file.enabled=false")
			.withBean(DataSource.class, () -> mock(DataSource.class))
			.run((context) -> {
				assertThat(context).doesNotHaveBean(FileStorage.class);
				assertThat(context).doesNotHaveBean(FileService.class);
				assertThat(context).doesNotHaveBean(FileController.class);
			});
	}

	@Test
	void backsOffStorageWhenUserDefinesBean() {
		FileStorage custom = mock(FileStorage.class);

		this.contextRunner.withBean(DataSource.class, () -> mock(DataSource.class))
			.withBean(FileStorage.class, () -> custom)
			.run((context) -> {
				assertThat(context).hasSingleBean(FileStorage.class);
				assertThat(context.getBean(FileStorage.class)).isSameAs(custom);
			});
	}

	@Test
	void backsOffServiceWhenUserDefinesBean() {
		FileService custom = new FileService(mock(FileStorage.class), new FileProperties());

		this.contextRunner.withBean(DataSource.class, () -> mock(DataSource.class))
			.withBean(FileService.class, () -> custom)
			.run((context) -> {
				assertThat(context).hasSingleBean(FileService.class);
				assertThat(context.getBean(FileService.class)).isSameAs(custom);
			});
	}

	@Test
	void bindsProperties() {
		this.contextRunner
			.withPropertyValues("butterfly.file.table-name=attachment", "butterfly.file.create-table=false",
					"butterfly.file.max-size=2MB", "butterfly.file.allowed-extensions=png,jpg",
					"butterfly.file.upload-path=/upload", "butterfly.file.download-path=/download")
			.run((context) -> {
				FileProperties properties = context.getBean(FileProperties.class);

				assertThat(properties.getTableName()).isEqualTo("attachment");
				assertThat(properties.isCreateTable()).isFalse();
				assertThat(properties.getMaxSize()).isEqualTo(DataSize.ofMegabytes(2));
				assertThat(properties.getAllowedExtensions()).containsExactly("png", "jpg");
				assertThat(properties.getUploadPath()).isEqualTo("/upload");
				assertThat(properties.getDownloadPath()).isEqualTo("/download");
			});
	}

	@Test
	void bindsDefaultProperties() {
		this.contextRunner.run((context) -> {
			FileProperties properties = context.getBean(FileProperties.class);

			assertThat(properties.isEnabled()).isTrue();
			assertThat(properties.getTableName()).isEqualTo("file");
			assertThat(properties.isCreateTable()).isTrue();
			assertThat(properties.getMaxSize()).isEqualTo(DataSize.ofMegabytes(10));
			assertThat(properties.getAllowedExtensions()).isEmpty();
			assertThat(properties.getUploadPath()).isEqualTo("/file/upload");
			assertThat(properties.getDownloadPath()).isEqualTo("/file");
		});
	}

	@Test
	void registersControllerInServletWebApplication() {
		this.webContextRunner.withBean(DataSource.class, () -> mock(DataSource.class)).run((context) -> {
			assertThat(context).hasSingleBean(FileService.class);
			assertThat(context).hasSingleBean(FileController.class);
		});
	}

	@Test
	void skipsControllerInNonWebApplication() {
		this.contextRunner.withBean(DataSource.class, () -> mock(DataSource.class)).run((context) -> {
			assertThat(context).hasSingleBean(FileService.class);
			assertThat(context).doesNotHaveBean(FileController.class);
		});
	}

	@Test
	void usesUserStorageEvenWithoutDataSource() {
		this.webContextRunner.withBean(FileStorage.class, () -> mock(FileStorage.class)).run((context) -> {
			assertThat(context).hasSingleBean(FileService.class);
			assertThat(context).hasSingleBean(FileController.class);
		});
	}

}
