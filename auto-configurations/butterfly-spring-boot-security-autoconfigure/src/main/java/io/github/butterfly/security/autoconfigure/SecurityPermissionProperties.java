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

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限收集配置,前缀 {@code butterfly.security.permission}.
 * <p>
 * 三个配置项分别为 {@code enabled}(总开关)、{@code db}(是否使用数据库存储)与 {@code scan-packages}(扫描包)。
 * getter/setter 由 {@code @Data} 生成,交给 Spring Boot 完成松散绑定。
 */
@Data
@ConfigurationProperties(prefix = "butterfly.security.permission")
public class SecurityPermissionProperties {

	/**
	 * 总开关,默认开启.
	 */
	private boolean enabled = true;

	/**
	 * 存在 {@code DataSource} 时是否使用数据库存储(自动建表 + Upsert). 关闭后无论是否有数据源都退化为内存存储。
	 */
	private boolean db = false;

	/**
	 * 需要扫描的包(使用方权限/分组枚举所在包). 为空时回退扫描 {@code @SpringBootApplication} 主类所在包。
	 */
	private List<String> scanPackages = new ArrayList<>();

}
