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

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 建表前对表名的校验:表名会拼进 SQL,必须限制为合法标识符.
 */
class MysqlFileStorageTests {

	private final DataSource dataSource = mock(DataSource.class);

	@Test
	void rejectsIllegalTableName() {
		FileProperties properties = new FileProperties();
		properties.setTableName("file; DROP TABLE user");

		assertThatThrownBy(() -> new MysqlFileStorage(this.dataSource, properties))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("非法的文件表名");
	}

	@Test
	void rejectsBlankTableName() {
		FileProperties properties = new FileProperties();
		properties.setTableName(" ");

		assertThatThrownBy(() -> new MysqlFileStorage(this.dataSource, properties))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("非法的文件表名");
	}

	@Test
	void rejectsTableNameStartingWithDigit() {
		FileProperties properties = new FileProperties();
		properties.setTableName("1file");

		assertThatThrownBy(() -> new MysqlFileStorage(this.dataSource, properties))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("非法的文件表名");
	}

	@Test
	void rejectsTableNameLongerThan64Characters() {
		FileProperties properties = new FileProperties();
		properties.setTableName("f".repeat(65));

		assertThatThrownBy(() -> new MysqlFileStorage(this.dataSource, properties))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("非法的文件表名");
	}

	@Test
	void acceptsCustomTableName() {
		FileProperties properties = new FileProperties();
		properties.setTableName("sys_file");

		assertThatCode(() -> new MysqlFileStorage(this.dataSource, properties)).doesNotThrowAnyException();
	}

}
