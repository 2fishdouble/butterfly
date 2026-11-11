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

import java.util.Optional;

/**
 * 文件存储 SPI.
 * <p>
 * 默认实现是 {@link MysqlFileStorage}(文件内容与元信息同表保存);使用方注册自己的 {@link FileStorage} Bean
 * 即可整体替换存储方式,{@link FileService} 的文件名校验、大小限制与扩展名过滤仍然生效。
 */
public interface FileStorage {

	/**
	 * 保存文件.
	 * @param upload 待保存的内容与元信息
	 * @return 入库后的元信息,包含主键、摘要与创建时间
	 */
	FileInfo store(FileUpload upload);

	/**
	 * 按主键读取文件.
	 * @param id 文件主键
	 * @return 文件元信息与内容;不存在时返回 {@link Optional#empty()}
	 */
	Optional<StoredFile> findById(String id);

	/**
	 * 按主键删除文件.
	 * @param id 文件主键
	 * @return 删除成功返回 {@code true},文件不存在返回 {@code false}
	 */
	boolean delete(String id);

}
