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

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * 自动收集权限/分组枚举并存储.
 * <ul>
 * <li>有 {@link DataSource} → {@link DbPermissionStorage} 建表入库;</li>
 * <li>无数据源 → {@link InMemoryPermissionStorage} 保底;</li>
 * <li>使用方自定义 {@link PermissionStorage} bean 时直接覆盖。</li>
 * </ul>
 * <p>
 * 由 {@code butterfly.security.permission.enabled} 控制,缺省(未配置)时视为开启; 关闭后不注册任何存储与收集器。仅当
 * {@code butterfly.security.permission.db=true} 且容器中存在 {@link DataSource} 时走数据库存储。
 */
@AutoConfiguration
@EnableConfigurationProperties(SecurityPermissionProperties.class)
@ConditionalOnProperty(prefix = "butterfly.security.permission", name = "enabled", havingValue = "true",
		matchIfMissing = true)
public class SecurityPermissionAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(PermissionStorage.class)
	PermissionStorage permissionStorage(SecurityPermissionProperties properties,
			ObjectProvider<DataSource> dataSourceProvider) {
		if (properties.isDb()) {
			DataSource dataSource = dataSourceProvider.getIfAvailable();
			if (dataSource != null) {
				return new DbPermissionStorage(dataSource);
			}
		}
		return new InMemoryPermissionStorage();
	}

	@Bean
	@ConditionalOnMissingBean(PermissionCollector.class)
	PermissionCollector permissionCollector(PermissionStorage storage, SecurityPermissionProperties properties,
			ApplicationContext context) {
		return new PermissionCollector(storage, properties, context);
	}

}
