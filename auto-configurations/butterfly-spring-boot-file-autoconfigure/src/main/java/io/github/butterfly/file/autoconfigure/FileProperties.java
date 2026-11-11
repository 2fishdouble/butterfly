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

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.util.ArrayList;
import java.util.List;

/**
 * 文件存储配置,前缀 {@code butterfly.file}.
 * <p>
 * 各字段都有默认值,引入模块即可使用;需要改表名、大小上限、允许的扩展名或接口路径时再覆盖。 getter/setter 由 {@code @Data} 生成,交给
 * Spring Boot 完成松散绑定。
 */
@Data
@ConfigurationProperties(prefix = "butterfly.file")
public class FileProperties {

	/**
	 * 总开关,默认开启. 关闭后不注册存储、服务与接口,也不会创建表.
	 */
	private boolean enabled = true;

	/**
	 * 文件表名,默认 {@code file}. 只允许字母、数字与下划线,且必须以字母或下划线开头,避免拼接 SQL 时被注入.
	 */
	private String tableName = "file";

	/**
	 * 是否在第一次读写时自动建表({@code CREATE TABLE IF NOT EXISTS}),默认开启.
	 * <p>
	 * 表已由 DBA 预先创建、或当前账号没有 DDL 权限时置为 {@code false}。
	 */
	private boolean createTable = true;

	/**
	 * 单个文件大小上限,默认 10MB. 需要同时小于 MySQL 的 {@code max_allowed_packet}。
	 */
	private DataSize maxSize = DataSize.ofMegabytes(10);

	/**
	 * 允许上传的扩展名(不带点、大小写不敏感),为空表示不限制.
	 */
	private List<String> allowedExtensions = new ArrayList<>();

	/**
	 * 上传接口路径,只接受 {@code multipart/form-data},表单字段名固定为 {@code file}.
	 */
	private String uploadPath = "/file/upload";

	/**
	 * 下载接口路径前缀(不要以 {@code /} 结尾),实际路径为 {@code {download-path}/{id}},默认 {@code /file} 即
	 * {@code GET /file/{id}}.
	 */
	private String downloadPath = "/file";

}
