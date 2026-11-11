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
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 文件存储自动配置:存在 {@code DataSource} 时装配 MySQL 单表存储与 {@link FileService}.
 * <p>
 * 表结构在第一次读写时按需创建(受 {@code butterfly.file.create-table} 控制),应用启动不要求数据库已可用。 使用方注册自己的
 * {@link FileStorage} Bean 即可替换存储实现(例如改存对象存储),注册 {@link FileService} 则整体接管校验与存储这一层;两者都用
 * {@link ConditionalOnMissingBean} 让位。
 * <p>
 * 用 {@code afterName} 而不是 {@code after = DataSourceAutoConfiguration.class} 声明顺序: Spring
 * Boot 4 把数据源自动配置拆到了独立的 {@code spring-boot-jdbc} 模块,本模块并不依赖它(没有 JDBC 依赖时
 * {@code DataSource} Bean 仍可能由使用方提供),用类名声明可以避免编译期耦合。
 */
@AutoConfiguration(afterName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
@ConditionalOnProperty(prefix = "butterfly.file", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(FileProperties.class)
public class FileAutoConfiguration {

	/**
	 * 默认的文件存储:元信息与内容写入 MySQL 单表.
	 * @param dataSource 数据源,由使用方引入的 JDBC 依赖提供
	 * @param properties 文件配置
	 * @return 基于当前数据源的 {@link MysqlFileStorage}
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(DataSource.class)
	FileStorage fileStorage(DataSource dataSource, FileProperties properties) {
		return new MysqlFileStorage(dataSource, properties);
	}

	/**
	 * 文件服务:统一做文件名校验、大小限制与扩展名过滤.
	 * @param fileStorage 存储实现,这里是上一个 Bean 或使用方自定义的实现
	 * @param properties 文件配置
	 * @return 绑定该存储与配置的 {@link FileService}
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(FileStorage.class)
	FileService fileService(FileStorage fileStorage, FileProperties properties) {
		return new FileService(fileStorage, properties);
	}

}
