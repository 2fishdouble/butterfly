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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 文件服务的校验逻辑:文件名清洗、大小限制、扩展名过滤与主键规范化.
 */
class FileServiceTests {

	private FileStorage fileStorage;

	private FileProperties properties;

	private FileService fileService;

	@BeforeEach
	void setUp() {
		this.fileStorage = mock(FileStorage.class);
		this.properties = new FileProperties();
		this.fileService = new FileService(this.fileStorage, this.properties);
		given(this.fileStorage.store(any())).willAnswer((invocation) -> infoFor(invocation.getArgument(0)));
	}

	@Test
	void storesFileWithSanitizedMetadata() {
		FileInfo info = this.fileService.store(stream("hello"), "..\\..\\a.txt", "text/plain", 5);

		FileUpload upload = captureUpload();
		assertThat(upload.originalName()).isEqualTo("a.txt");
		assertThat(upload.content()).containsExactly("hello".getBytes(StandardCharsets.UTF_8));
		assertThat(upload.contentType()).isEqualTo("text/plain");
		assertThat(info.originalName()).isEqualTo("a.txt");
	}

	@Test
	void storesInMemoryContent() {
		this.fileService.store("hi".getBytes(StandardCharsets.UTF_8), "a.bin", null);

		FileUpload upload = captureUpload();
		assertThat(upload.originalName()).isEqualTo("a.bin");
		assertThat(upload.content()).containsExactly("hi".getBytes(StandardCharsets.UTF_8));
		assertThat(upload.contentType()).isNull();
	}

	@Test
	void stripsControlCharactersFromFileName() {
		this.fileService.store(stream("x"), "a\r\nb.txt", null, 1);

		assertThat(captureUpload().originalName()).isEqualTo("ab.txt");
	}

	@Test
	void rejectsBlankFileName() {
		assertThatThrownBy(() -> this.fileService.store(stream("x"), " ", null, 1))
			.isInstanceOf(BusinessException.class)
			.hasMessage("文件名不能为空");
		assertThatThrownBy(() -> this.fileService.store(stream("x"), null, null, 1))
			.isInstanceOf(BusinessException.class)
			.hasMessage("文件名不能为空");
		assertThatThrownBy(() -> this.fileService.store(stream("x"), "../", null, 1))
			.isInstanceOf(BusinessException.class)
			.hasMessage("文件名不能为空");
		verifyNoInteractions(this.fileStorage);
	}

	@Test
	void rejectsTooLongFileName() {
		String fileName = "a".repeat(252) + ".txt";

		assertThatThrownBy(() -> this.fileService.store(stream("x"), fileName, null, 1))
			.isInstanceOf(BusinessException.class)
			.hasMessageContaining("文件名长度不能超过");
	}

	@Test
	void rejectsEmptyContent() {
		assertThatThrownBy(() -> this.fileService.store(stream(""), "a.txt", null, 0))
			.isInstanceOf(BusinessException.class)
			.hasMessage("文件内容不能为空");
		verifyNoInteractions(this.fileStorage);
	}

	@Test
	void rejectsDeclaredSizeAboveLimit() {
		this.properties.setMaxSize(DataSize.ofBytes(4));

		assertThatThrownBy(() -> this.fileService.store(stream("hello"), "a.txt", null, 5))
			.isInstanceOf(BusinessException.class)
			.hasMessage("文件大小不能超过 4B");
		verifyNoInteractions(this.fileStorage);
	}

	@Test
	void rejectsStreamLargerThanDeclaredSize() {
		this.properties.setMaxSize(DataSize.ofBytes(4));

		assertThatThrownBy(() -> this.fileService.store(stream("0123456789"), "a.txt", null, 2))
			.isInstanceOf(BusinessException.class)
			.hasMessage("文件大小不能超过 4B");
		verifyNoInteractions(this.fileStorage);
	}

	@Test
	void rejectsDisallowedExtension() {
		this.properties.setAllowedExtensions(List.of("png"));
		this.fileService = new FileService(this.fileStorage, this.properties);

		assertThatThrownBy(() -> this.fileService.store(stream("hello"), "a.txt", null, 5))
			.isInstanceOf(BusinessException.class)
			.hasMessageContaining("不支持的文件类型: txt")
			.hasMessageContaining("允许: png");
		verifyNoInteractions(this.fileStorage);
	}

	@Test
	void matchesExtensionIgnoringCaseAndLeadingDot() {
		this.properties.setAllowedExtensions(List.of(".PNG"));
		this.fileService = new FileService(this.fileStorage, this.properties);

		this.fileService.store(stream("hello"), "a.Png", null, 5);

		assertThat(captureUpload().originalName()).isEqualTo("a.Png");
	}

	@Test
	void allowsAnyExtensionWhenListIsEmpty() {
		this.fileService.store(stream("hello"), "a.unknown", null, 5);

		verify(this.fileStorage).store(any());
	}

	@Test
	void dropsIllegalContentType() {
		this.fileService.store(stream("hello"), "a.txt", "text/plain\r\nX-Evil: 1", 5);

		assertThat(captureUpload().contentType()).isNull();
	}

	@Test
	void loadsStoredFileAndNormalizesId() {
		String id = "f".repeat(32);
		StoredFile stored = new StoredFile(infoFor(new FileUpload("a.txt", null, new byte[] { 1 })), new byte[] { 1 });
		given(this.fileStorage.findById(id)).willReturn(Optional.of(stored));

		assertThat(this.fileService.load(id.toUpperCase(Locale.ROOT))).isSameAs(stored);
	}

	@Test
	void rejectsUnknownFile() {
		given(this.fileStorage.findById("f".repeat(32))).willReturn(Optional.empty());

		assertThatThrownBy(() -> this.fileService.load("f".repeat(32))).isInstanceOf(BusinessException.class)
			.hasMessageContaining("文件不存在");
	}

	@Test
	void rejectsMalformedIdWithoutTouchingStorage() {
		assertThatThrownBy(() -> this.fileService.load("not-a-file-id")).isInstanceOf(BusinessException.class)
			.hasMessageContaining("文件不存在");
		assertThatThrownBy(() -> this.fileService.delete("../../etc/passwd")).isInstanceOf(BusinessException.class)
			.hasMessageContaining("文件不存在");
		verifyNoInteractions(this.fileStorage);
	}

	@Test
	void deletesById() {
		String id = "f".repeat(32);
		given(this.fileStorage.delete(id)).willReturn(true);

		assertThat(this.fileService.delete(id)).isTrue();
		verify(this.fileStorage).delete(id);
	}

	private FileUpload captureUpload() {
		ArgumentCaptor<FileUpload> captor = ArgumentCaptor.forClass(FileUpload.class);
		verify(this.fileStorage).store(captor.capture());
		return Objects.requireNonNull(captor.getValue());
	}

	private static InputStream stream(String content) {
		return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
	}

	private static FileInfo infoFor(FileUpload upload) {
		return new FileInfo("f".repeat(32), upload.originalName(), upload.contentType(), upload.content().length,
				"0".repeat(64), LocalDateTime.of(2026, 1, 2, 3, 4, 5));
	}

}
