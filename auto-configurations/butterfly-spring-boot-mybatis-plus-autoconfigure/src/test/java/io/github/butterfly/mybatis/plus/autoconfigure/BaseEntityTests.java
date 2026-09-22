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

package io.github.butterfly.mybatis.plus.autoconfigure;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 固定基类向使用方承诺的列名与填充/逻辑删除声明.
 */
class BaseEntityTests {

	@Test
	void mapsCreateTimeToInsertFillColumn() throws Exception {
		TableField tableField = field("createTime").getAnnotation(TableField.class);

		assertThat(tableField.value()).isEqualTo("create_time");
		assertThat(tableField.fill()).isEqualTo(FieldFill.INSERT);
	}

	@Test
	void mapsEditTimeToInsertUpdateFillColumn() throws Exception {
		TableField tableField = field("editTime").getAnnotation(TableField.class);

		assertThat(tableField.value()).isEqualTo("edit_time");
		assertThat(tableField.fill()).isEqualTo(FieldFill.INSERT_UPDATE);
	}

	@Test
	void mapsOperatorFieldsToInsertAndUpdateColumns() throws Exception {
		assertThat(field("creatorId").getAnnotation(TableField.class).fill()).isEqualTo(FieldFill.INSERT);
		assertThat(field("editorId").getAnnotation(TableField.class).fill()).isEqualTo(FieldFill.INSERT_UPDATE);
	}

	@Test
	void mapsIsDeletedAsLogicDeleteColumn() throws Exception {
		Field isDeleted = field("isDeleted");

		assertThat(isDeleted.getAnnotation(TableField.class).value()).isEqualTo("is_deleted");
		assertThat(isDeleted.getAnnotation(TableLogic.class)).isNotNull();
	}

	private static Field field(String name) throws NoSuchFieldException {
		return BaseEntity.class.getDeclaredField(name);
	}

}
