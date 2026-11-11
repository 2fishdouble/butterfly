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

import javax.sql.DataSource;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * MySQL 单表存储:元信息与文件内容都写入 {@code file} 表,读写走纯 JDBC.
 * <p>
 * 选纯 JDBC 而不是 MyBatis/JPA,是为了不把文件能力绑到使用方的 ORM 上;建表语句为 MySQL 方言,换库请自行替换。 建表是惰性的:第一次读写时才执行
 * {@code CREATE TABLE IF NOT EXISTS},因此应用启动不要求数据库已可用; 建表失败不会缓存失败状态,下次调用会重试。
 */
public class MysqlFileStorage implements FileStorage {

	private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

	private static final int MAX_TABLE_NAME_LENGTH = 64;

	private final DataSource dataSource;

	private final String tableName;

	private final boolean createTable;

	private final String createTableSql;

	private final String insertSql;

	private final String selectSql;

	private final String deleteSql;

	/**
	 * 建表是否成功. 建表只做一次,失败的异常直接抛给调用方,不置位,便于下次重试.
	 */
	private volatile boolean schemaReady;

	/**
	 * 使用数据源与配置创建存储.
	 * @param dataSource 文件表所在的数据源
	 * @param properties 文件配置,读取表名与是否自动建表
	 * @throws IllegalArgumentException 表名不是合法标识符时抛出
	 */
	public MysqlFileStorage(DataSource dataSource, FileProperties properties) {
		this.dataSource = dataSource;
		this.tableName = validTableName(properties.getTableName());
		this.createTable = properties.isCreateTable();
		this.createTableSql = """
				CREATE TABLE IF NOT EXISTS `%s` (
				  `id` char(32) NOT NULL COMMENT '主键,32 位十六进制 UUID',
				  `original_name` varchar(255) NOT NULL COMMENT '原始文件名',
				  `content_type` varchar(255) DEFAULT NULL COMMENT '内容类型',
				  `file_size` bigint NOT NULL COMMENT '文件字节数',
				  `sha256` char(64) NOT NULL COMMENT '内容的 SHA-256 摘要',
				  `content` longblob NOT NULL COMMENT '文件内容',
				  `create_time` datetime(3) NOT NULL COMMENT '创建时间',
				  PRIMARY KEY (`id`),
				  KEY `idx_sha256` (`sha256`)
				) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='文件表'"""
			.formatted(this.tableName);
		this.insertSql = "INSERT INTO `" + this.tableName
				+ "` (`id`, `original_name`, `content_type`, `file_size`, `sha256`, `content`, `create_time`)"
				+ " VALUES (?, ?, ?, ?, ?, ?, ?)";
		this.selectSql = "SELECT `id`, `original_name`, `content_type`, `file_size`, `sha256`, `content`,"
				+ " `create_time` FROM `" + this.tableName + "` WHERE `id` = ?";
		this.deleteSql = "DELETE FROM `" + this.tableName + "` WHERE `id` = ?";
	}

	/**
	 * 保存文件:生成 32 位十六进制 UUID 作为主键,计算 SHA-256 摘要,连同内容一起写入表中.
	 * @param upload 待保存的内容与元信息
	 * @return 入库后的元信息
	 * @throws IllegalStateException 建表或写入数据库失败时抛出,原始 {@code SQLException} 作为 cause 保留
	 */
	@Override
	public FileInfo store(FileUpload upload) {
		ensureSchema();
		byte[] content = upload.content();
		String id = UUID.randomUUID().toString().replace("-", "");
		String sha256 = sha256Hex(content);
		String contentType = upload.contentType();
		LocalDateTime createTime = LocalDateTime.now(ZoneId.systemDefault()).truncatedTo(ChronoUnit.MILLIS);
		try (Connection connection = this.dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(this.insertSql)) {
			statement.setString(1, id);
			statement.setString(2, upload.originalName());
			if (contentType == null) {
				statement.setNull(3, Types.VARCHAR);
			}
			else {
				statement.setString(3, contentType);
			}
			statement.setLong(4, content.length);
			statement.setString(5, sha256);
			statement.setBytes(6, content);
			statement.setObject(7, createTime);
			statement.executeUpdate();
		}
		catch (SQLException ex) {
			throw new IllegalStateException("保存文件到表 " + this.tableName + " 失败", ex);
		}
		return new FileInfo(id, upload.originalName(), contentType, content.length, sha256, createTime);
	}

	/**
	 * 按主键读取文件.
	 * @param id 文件主键
	 * @return 文件元信息与内容;不存在时返回 {@link Optional#empty()}
	 * @throws IllegalStateException 建表或查询失败时抛出,原始 {@code SQLException} 作为 cause 保留
	 */
	@Override
	public Optional<StoredFile> findById(String id) {
		ensureSchema();
		try (Connection connection = this.dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(this.selectSql)) {
			statement.setString(1, id);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					return Optional.empty();
				}
				FileInfo info = new FileInfo(Objects.requireNonNull(resultSet.getString("id"), "文件表列 id 不允许为空"),
						Objects.requireNonNull(resultSet.getString("original_name"), "文件表列 original_name 不允许为空"),
						resultSet.getString("content_type"), resultSet.getLong("file_size"),
						Objects.requireNonNull(resultSet.getString("sha256"), "文件表列 sha256 不允许为空"),
						Objects.requireNonNull(resultSet.getObject("create_time", LocalDateTime.class),
								"文件表列 create_time 不允许为空"));
				byte[] content = Objects.requireNonNull(resultSet.getBytes("content"), "文件表列 content 不允许为空");
				return Optional.of(new StoredFile(info, content));
			}
		}
		catch (SQLException ex) {
			throw new IllegalStateException("从表 " + this.tableName + " 读取文件失败", ex);
		}
	}

	/**
	 * 按主键删除文件.
	 * @param id 文件主键
	 * @return 删除成功返回 {@code true},文件不存在返回 {@code false}
	 * @throws IllegalStateException 建表或删除失败时抛出,原始 {@code SQLException} 作为 cause 保留
	 */
	@Override
	public boolean delete(String id) {
		ensureSchema();
		try (Connection connection = this.dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(this.deleteSql)) {
			statement.setString(1, id);
			return statement.executeUpdate() > 0;
		}
		catch (SQLException ex) {
			throw new IllegalStateException("从表 " + this.tableName + " 删除文件失败", ex);
		}
	}

	private void ensureSchema() {
		if (!this.createTable || this.schemaReady) {
			return;
		}
		synchronized (this) {
			if (this.schemaReady) {
				return;
			}
			try (Connection connection = this.dataSource.getConnection();
					Statement statement = connection.createStatement()) {
				statement.execute(this.createTableSql);
				this.schemaReady = true;
			}
			catch (SQLException ex) {
				throw new IllegalStateException("创建文件表 " + this.tableName + " 失败", ex);
			}
		}
	}

	private static String validTableName(String tableName) {
		String name = tableName.strip();
		if (name.isEmpty() || name.length() > MAX_TABLE_NAME_LENGTH || !TABLE_NAME_PATTERN.matcher(name).matches()) {
			throw new IllegalArgumentException("非法的文件表名: " + tableName + " (只允许字母、数字与下划线,长度 1-64,且必须以字母或下划线开头)");
		}
		return name;
	}

	private static String sha256Hex(byte[] content) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("当前 JVM 不支持 SHA-256", ex);
		}
	}

}
