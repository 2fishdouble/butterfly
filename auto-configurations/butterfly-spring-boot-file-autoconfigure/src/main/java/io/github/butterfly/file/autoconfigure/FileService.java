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

import io.github.butterfly.core.BusinessException;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 文件服务:统一做文件名清洗、大小与扩展名校验,再委托 {@link FileStorage} 落库.
 * <p>
 * 上传/下载接口以及业务代码都走这一层,所以「什么文件能进、能进多大」只在这里定义一次。
 */
public class FileService {

	private static final Pattern FILE_ID_PATTERN = Pattern.compile("[0-9a-f]{32}");

	private static final int MAX_FILE_NAME_LENGTH = 255;

	private static final int MAX_CONTENT_TYPE_LENGTH = 255;

	private static final int BUFFER_SIZE = 8192;

	/**
	 * 读取时给 {@link ByteArrayOutputStream} 的初始容量上限,避免按声明大小一次性分配大数组.
	 */
	private static final int MAX_INITIAL_CAPACITY = 1 << 20;

	private final FileStorage fileStorage;

	private final FileProperties properties;

	private final Set<String> allowedExtensions;

	/**
	 * 创建文件服务.
	 * @param fileStorage 实际存储实现
	 * @param properties 文件配置
	 */
	public FileService(FileStorage fileStorage, FileProperties properties) {
		this.fileStorage = fileStorage;
		this.properties = properties;
		this.allowedExtensions = properties.getAllowedExtensions()
			.stream()
			.map((extension) -> extension.toLowerCase(Locale.ROOT).strip())
			.map((extension) -> extension.startsWith(".") ? extension.substring(1) : extension)
			.filter((extension) -> !extension.isEmpty())
			.collect(Collectors.toUnmodifiableSet());
	}

	/**
	 * 校验并保存上传的文件.
	 * @param inputStream 文件内容流,读取过程中不做缓冲复用
	 * @param originalName 原始文件名,可为 {@code null};会去掉路径与不可见控制字符
	 * @param contentType 内容类型,可为 {@code null};包含换行等非法字符时按 {@code null} 落库
	 * @param size 声明的字节数,必须大于 0 且不超过 {@code butterfly.file.max-size}
	 * @return 入库后的文件元信息
	 * @throws BusinessException 文件名为空、大小非法、扩展名不允许或读取失败时抛出
	 */
	public FileInfo store(InputStream inputStream, @Nullable String originalName, @Nullable String contentType,
			long size) {
		String fileName = sanitizeFileName(originalName);
		long maxSize = this.properties.getMaxSize().toBytes();
		if (size <= 0) {
			throw new BusinessException("文件内容不能为空");
		}
		if (size > maxSize) {
			throw new BusinessException("文件大小不能超过 " + this.properties.getMaxSize());
		}
		checkExtension(fileName);
		byte[] content = readContent(inputStream, size, maxSize);
		return this.fileStorage.store(new FileUpload(fileName, normalizeContentType(contentType), content));
	}

	/**
	 * 校验并保存内存中的文件内容,便于业务代码在非 Web 场景直接调用.
	 * @param content 文件内容,不能为空
	 * @param originalName 原始文件名,可为 {@code null}
	 * @param contentType 内容类型,可为 {@code null}
	 * @return 入库后的文件元信息
	 * @throws BusinessException 文件名为空、大小非法、扩展名不允许时抛出
	 */
	public FileInfo store(byte[] content, @Nullable String originalName, @Nullable String contentType) {
		return store(new ByteArrayInputStream(content), originalName, contentType, content.length);
	}

	/**
	 * 按主键读取文件.
	 * @param id 文件主键,32 位十六进制字符串
	 * @return 文件元信息与内容
	 * @throws BusinessException 主键格式非法或文件不存在时抛出
	 */
	public StoredFile load(String id) {
		String fileId = normalizeId(id);
		return this.fileStorage.findById(fileId).orElseThrow(() -> new BusinessException("文件不存在: " + id));
	}

	/**
	 * 按主键删除文件.
	 * @param id 文件主键,32 位十六进制字符串
	 * @return 删除成功返回 {@code true},文件不存在返回 {@code false}
	 * @throws BusinessException 主键格式非法时抛出
	 */
	public boolean delete(String id) {
		return this.fileStorage.delete(normalizeId(id));
	}

	private byte[] readContent(InputStream inputStream, long statedSize, long maxSize) {
		ByteArrayOutputStream output = new ByteArrayOutputStream(initialCapacity(statedSize));
		byte[] buffer = new byte[BUFFER_SIZE];
		long total = 0;
		try {
			int read;
			while ((read = inputStream.read(buffer)) != -1) {
				total += read;
				if (total > maxSize) {
					throw new BusinessException("文件大小不能超过 " + this.properties.getMaxSize());
				}
				output.write(buffer, 0, read);
			}
		}
		catch (IOException ex) {
			throw new BusinessException("读取上传文件失败", ex);
		}
		if (total == 0) {
			throw new BusinessException("文件内容不能为空");
		}
		return output.toByteArray();
	}

	private static int initialCapacity(long statedSize) {
		return Math.clamp(statedSize, 0, MAX_INITIAL_CAPACITY);
	}

	private void checkExtension(String fileName) {
		if (this.allowedExtensions.isEmpty()) {
			return;
		}
		String extension = StringUtils.getFilenameExtension(fileName);
		String normalized = (extension != null) ? extension.toLowerCase(Locale.ROOT) : "";
		if (!this.allowedExtensions.contains(normalized)) {
			throw new BusinessException("不支持的文件类型: " + ((extension != null) ? extension : "无扩展名") + ", 允许: "
					+ String.join(", ", this.properties.getAllowedExtensions()));
		}
	}

	private static String sanitizeFileName(@Nullable String originalName) {
		if (originalName == null || originalName.isBlank()) {
			throw new BusinessException("文件名不能为空");
		}
		String name = originalName.replace('\\', '/');
		int lastSlash = name.lastIndexOf('/');
		if (lastSlash >= 0) {
			name = name.substring(lastSlash + 1);
		}
		name = name.replaceAll("\\p{Cntrl}", "").strip();
		if (name.isEmpty() || ".".equals(name) || "..".equals(name)) {
			throw new BusinessException("文件名不能为空");
		}
		if (name.length() > MAX_FILE_NAME_LENGTH) {
			throw new BusinessException("文件名长度不能超过 " + MAX_FILE_NAME_LENGTH + " 个字符");
		}
		return name;
	}

	private static @Nullable String normalizeContentType(@Nullable String contentType) {
		if (contentType == null) {
			return null;
		}
		String value = contentType.strip();
		if (value.isEmpty() || value.length() > MAX_CONTENT_TYPE_LENGTH || value.indexOf('\r') >= 0
				|| value.indexOf('\n') >= 0 || value.indexOf('\0') >= 0) {
			return null;
		}
		return value;
	}

	private static String normalizeId(String id) {
		String value = id.strip().toLowerCase(Locale.ROOT);
		if (!FILE_ID_PATTERN.matcher(value).matches()) {
			throw new BusinessException("文件不存在: " + id);
		}
		return value;
	}

}
