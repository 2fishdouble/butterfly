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

import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;

/**
 * 文件元信息,不包含文件内容,由上传接口返回.
 *
 * @param id 文件主键,32 位十六进制 UUID,下载接口用它定位文件
 * @param originalName 上传时的原始文件名(已去掉路径与不可见控制字符)
 * @param contentType 内容类型,上传时未提供则为 {@code null}
 * @param size 文件字节数
 * @param sha256 文件内容的 SHA-256 摘要(小写十六进制)
 * @param createTime 入库时间(毫秒精度)
 */
public record FileInfo(String id, String originalName, @Nullable String contentType, long size, String sha256,
		LocalDateTime createTime) {
}
