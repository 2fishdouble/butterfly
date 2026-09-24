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

package io.github.butterfly.canal.autoconfigure;

import io.github.butterfly.core.BaseEnum;
import lombok.Data;
import lombok.Getter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 校验 {@link BeanWrapperCanalRowMapper} 的属性绑定:实现 {@link BaseEnum} 的枚举按 code、title、枚举名匹配,
 * 普通枚举仍按枚举名匹配,MySQL 常见的空格分隔日期时间与数字列也能转换。
 */
class BeanWrapperCanalRowMapperTests {

	private final BeanWrapperCanalRowMapper mapper = new BeanWrapperCanalRowMapper();

	@Test
	void mapsBaseEnumByCodeTitleAndName() {
		assertThat(map("coded", "0").getCoded()).isEqualTo(CodedWeek.MONDAY);
		assertThat(map("coded", "6").getCoded()).isEqualTo(CodedWeek.SUNDAY);
		assertThat(map("coded", "周一").getCoded()).isEqualTo(CodedWeek.MONDAY);
		assertThat(map("coded", "monday").getCoded()).isEqualTo(CodedWeek.MONDAY);
		assertThat(map("coded", " SUNDAY ").getCoded()).isEqualTo(CodedWeek.SUNDAY);
	}

	@Test
	void keepsPlainEnumConvertedByName() {
		assertThat(map("plain", "SUNDAY").getPlain()).isEqualTo(PlainWeek.SUNDAY);
	}

	@Test
	void failsWhenTheValueMatchesNoConstant() {
		assertThatThrownBy(() -> map("coded", "9")).isInstanceOf(BeansException.class);
	}

	@Test
	void convertsOtherPropertyTypes() {
		Row row = this.mapper.map(Map.of("create_time", "2024-01-01 10:00:00", "cuda_cores", "5120"), Row.class);

		assertThat(row.getCreateTime()).isEqualTo(LocalDateTime.of(2024, 1, 1, 10, 0));
		assertThat(row.getCudaCores()).isEqualTo(5120);
	}

	private Row map(String property, String value) {
		return this.mapper.map(Map.of(property, value), Row.class);
	}

	/**
	 * 行数据实体:同时包含编码枚举、普通枚举与其它基础类型.
	 */
	@Data
	static class Row {

		private CodedWeek coded;

		private PlainWeek plain;

		private LocalDateTime createTime;

		private Integer cudaCores;

	}

	/**
	 * 编码枚举:库里存 code,也能按标题或枚举名匹配.
	 */
	@Getter
	enum CodedWeek implements BaseEnum {

		MONDAY(0, "周一"),

		SUNDAY(6, "周日");

		private final int code;

		private final String title;

		CodedWeek(int code, String title) {
			this.code = code;
			this.title = title;
		}

	}

	/**
	 * 普通枚举:仍由 Spring 的默认规则按枚举名匹配.
	 */
	enum PlainWeek {

		MONDAY,

		SUNDAY

	}

}
