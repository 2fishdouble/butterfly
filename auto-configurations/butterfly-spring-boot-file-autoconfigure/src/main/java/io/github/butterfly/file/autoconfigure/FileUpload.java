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

/**
 * 待入库的文件内容与元信息,由 {@link FileService} 构造后交给 {@link FileStorage}.
 * <p>
 * 内容是内部流转的一次性数组,不对外复用,因此按数组原样承载,不做防御性拷贝。
 *
 * @param originalName 已清洗的原始文件名
 * @param contentType 内容类型,可为 {@code null}
 * @param content 文件内容,调用方保证非空且不超过大小上限
 */
@SuppressWarnings("ArrayRecordComponent")
public record FileUpload(String originalName, @Nullable String contentType, byte[] content) {
}
