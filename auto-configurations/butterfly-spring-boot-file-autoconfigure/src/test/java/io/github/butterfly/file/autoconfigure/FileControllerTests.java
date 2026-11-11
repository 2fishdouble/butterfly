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

import io.github.butterfly.core.R;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 上传/下载接口的单元测试:返回结构、响应头、内容类型回退与默认路径映射.
 */
class FileControllerTests {

	private final FileStorage fileStorage = new InMemoryFileStorage();

	private final FileProperties properties = new FileProperties();

	private final FileService fileService = new FileService(this.fileStorage, this.properties);

	private final FileController fileController = new FileController(this.fileService);

	@Test
	void uploadReturnsFileMetadata() {
		MockMultipartFile file = new MockMultipartFile("file", "hello.txt", "text/plain",
				"hello".getBytes(StandardCharsets.UTF_8));

		R<FileInfo> response = this.fileController.upload(file);

		assertThat(response.getCode()).isEqualTo(200);
		assertThat(response.getIsSuccess()).isTrue();
		FileInfo data = Objects.requireNonNull(response.getData());
		assertThat(data.originalName()).isEqualTo("hello.txt");
		assertThat(data.contentType()).isEqualTo("text/plain");
		assertThat(data.size()).isEqualTo(5);
	}

	@Test
	void uploadStripsPathFromFileName() {
		MockMultipartFile file = new MockMultipartFile("file", "../evil.txt", "text/plain",
				"x".getBytes(StandardCharsets.UTF_8));

		FileInfo data = Objects.requireNonNull(this.fileController.upload(file).getData());

		assertThat(data.originalName()).isEqualTo("evil.txt");
	}

	@Test
	void downloadReturnsAttachmentWithOriginalName() throws Exception {
		FileInfo info = this.fileService.store("报告".getBytes(StandardCharsets.UTF_8), "报告.txt", "text/plain");

		ResponseEntity<Resource> response = this.fileController.download(info.id());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_PLAIN);
		ContentDisposition disposition = Objects.requireNonNull(response.getHeaders().getContentDisposition());
		assertThat(disposition.getType()).isEqualTo("attachment");
		assertThat(disposition.getFilename()).isEqualTo("报告.txt");
		Resource body = Objects.requireNonNull(response.getBody());
		assertThat(body.getContentAsByteArray()).isEqualTo("报告".getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void downloadFallsBackToOctetStreamForUnparsableContentType() {
		FileInfo info = this.fileService.store(new byte[] { 1, 2 }, "a.bin", "not a media type");

		ResponseEntity<Resource> response = this.fileController.download(info.id());

		assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
	}

	@Test
	void exposesDefaultPaths() throws Exception {
		MockMvc mockMvc = MockMvcBuilders.standaloneSetup(this.fileController).build();
		byte[] content = "hello".getBytes(StandardCharsets.UTF_8);

		MvcResult uploadResult = mockMvc
			.perform(multipart("/file/upload").file(new MockMultipartFile("file", "hello.txt", "text/plain", content)))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andReturn();
		assertThat(uploadResult.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("hello.txt");

		FileInfo seeded = this.fileService.store(content, "hello.txt", "text/plain");
		MvcResult downloadResult = mockMvc.perform(get("/file/{id}", seeded.id()))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
			.andReturn();
		assertThat(downloadResult.getResponse().getContentAsByteArray()).isEqualTo(content);
	}

	/**
	 * 内存存储:只用于接口测试,避免依赖数据库.
	 */
	private static final class InMemoryFileStorage implements FileStorage {

		private final Map<String, StoredFile> files = new ConcurrentHashMap<>();

		@Override
		public FileInfo store(FileUpload upload) {
			String id = UUID.randomUUID().toString().replace("-", "");
			FileInfo info = new FileInfo(id, upload.originalName(), upload.contentType(), upload.content().length,
					"0".repeat(64), LocalDateTime.now(ZoneId.systemDefault()));
			this.files.put(id, new StoredFile(info, upload.content().clone()));
			return info;
		}

		@Override
		public Optional<StoredFile> findById(String id) {
			return Optional.ofNullable(this.files.get(id));
		}

		@Override
		public boolean delete(String id) {
			return this.files.remove(id) != null;
		}

	}

}
