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

import com.mysql.cj.jdbc.MysqlDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用真实 MySQL 容器验证建表语句、二进制读写与删除.
 * <p>
 * 前置条件是本机 Docker 已启动;Docker 不可用时整个测试类被跳过 ({@code disabledWithoutDocker = true}),而不是失败。
 */
@Testcontainers(disabledWithoutDocker = true)
class MysqlFileStorageTestcontainersTests {

	private static final String HELLO_SHA256 = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";

	@Container
	@SuppressWarnings("rawtypes")
	private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
		.withDatabaseName("butterfly")
		.withUsername("butterfly")
		.withPassword("butterfly");

	private MysqlFileStorage fileStorage;

	@BeforeEach
	void setUp() throws SQLException {
		MysqlDataSource dataSource = new MysqlDataSource();
		dataSource.setURL(MYSQL.getJdbcUrl());
		dataSource.setUser(MYSQL.getUsername());
		dataSource.setPassword(MYSQL.getPassword());
		this.fileStorage = new MysqlFileStorage(dataSource, new FileProperties());
	}

	@Test
	void createsTableOnFirstUseAndRoundTripsFile() {
		byte[] content = "hello".getBytes(StandardCharsets.UTF_8);

		FileInfo info = this.fileStorage.store(new FileUpload("问候.txt", "text/plain", content));

		assertThat(info.id()).matches("[0-9a-f]{32}");
		assertThat(info.size()).isEqualTo(content.length);
		assertThat(info.sha256()).isEqualTo(HELLO_SHA256);

		StoredFile stored = this.fileStorage.findById(info.id()).orElseThrow();
		assertThat(stored.info()).isEqualTo(info);
		assertThat(stored.content()).isEqualTo(content);
	}

	@Test
	void storesNullContentType() {
		FileInfo info = this.fileStorage.store(new FileUpload("a.bin", null, new byte[] { 1, 2, 3 }));

		assertThat(this.fileStorage.findById(info.id()).orElseThrow().info().contentType()).isNull();
	}

	@Test
	void returnsEmptyForUnknownFile() {
		assertThat(this.fileStorage.findById("0".repeat(32))).isEmpty();
	}

	@Test
	void deletesStoredFile() {
		FileInfo info = this.fileStorage.store(new FileUpload("a.txt", "text/plain", new byte[] { 1 }));

		assertThat(this.fileStorage.delete(info.id())).isTrue();
		assertThat(this.fileStorage.findById(info.id())).isEmpty();
		assertThat(this.fileStorage.delete(info.id())).isFalse();
	}

}
