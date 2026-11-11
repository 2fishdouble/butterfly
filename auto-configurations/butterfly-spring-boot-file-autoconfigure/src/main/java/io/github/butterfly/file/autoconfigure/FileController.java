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
import io.github.butterfly.core.R;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 文件上传/下载接口,默认 {@code POST /file/upload} 与 {@code GET /file/{id}}.
 * <p>
 * 两个路径分别由 {@code butterfly.file.upload-path} 与 {@code butterfly.file.download-path} 决定
 * (注解上直接取值,不需要重新注册 handler);上传只接受 {@code multipart/form-data},表单字段名固定为
 * {@code file}。上传返回统一响应 {@link R},下载直接回二进制流并带 {@code Content-Disposition: attachment}。
 */
@RestController
public class FileController {

	private final FileService fileService;

	/**
	 * 创建文件接口.
	 * @param fileService 文件服务
	 */
	public FileController(FileService fileService) {
		this.fileService = fileService;
	}

	/**
	 * 上传文件.
	 * @param file 上传的文件,字段名固定为 {@code file}
	 * @return 成功时携带文件元信息(主键、文件名、大小、摘要与创建时间)
	 * @throws BusinessException 文件为空、超限、扩展名不允许或读取失败时抛出
	 */
	@PostMapping("${butterfly.file.upload-path:/file/upload}")
	public R<FileInfo> upload(@RequestPart("file") MultipartFile file) {
		try {
			return R.success(this.fileService.store(file.getInputStream(), file.getOriginalFilename(),
					file.getContentType(), file.getSize()));
		}
		catch (IOException ex) {
			throw new BusinessException("读取上传文件失败", ex);
		}
	}

	/**
	 * 下载文件.
	 * @param id 文件主键
	 * @return 文件内容与下载响应头,文件名按 RFC 5987 编码以支持中文
	 * @throws BusinessException 主键格式非法或文件不存在时抛出
	 */
	@GetMapping("${butterfly.file.download-path:/file/}/{id}")
	public ResponseEntity<Resource> download(@PathVariable("id") String id) {
		StoredFile storedFile = this.fileService.load(id);
		return ResponseEntity.ok()
			.headers(downloadHeaders(storedFile.info()))
			.body(new ByteArrayResource(storedFile.content()));
	}

	private static HttpHeaders downloadHeaders(FileInfo info) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(mediaType(info.contentType()));
		headers.setContentDisposition(
				ContentDisposition.attachment().filename(info.originalName(), StandardCharsets.UTF_8).build());
		return headers;
	}

	private static MediaType mediaType(@Nullable String contentType) {
		if (contentType != null) {
			try {
				return MediaType.parseMediaType(contentType);
			}
			catch (InvalidMediaTypeException ignored) {
				// 内容类型是上传方给的,解析不了就按二进制流返回
			}
		}
		return MediaType.APPLICATION_OCTET_STREAM;
	}

}
