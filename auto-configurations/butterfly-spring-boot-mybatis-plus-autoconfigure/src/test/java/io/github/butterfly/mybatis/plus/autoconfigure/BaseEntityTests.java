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
