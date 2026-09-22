package io.github.butterfly.mybatis.plus.autoconfigure;

import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 覆盖审计字段自动填充的插入/更新分支与无审计字段实体的容错.
 */
class BaseEntityHandlerTests {

	private final BaseEntityHandler handler = new BaseEntityHandler();

	@Test
	void insertFillSetsBothAuditTimesWhenNull() {
		BaseEntity entity = new BaseEntity();

		this.handler.insertFill(SystemMetaObject.forObject(entity));

		assertThat(entity.getCreateTime()).isNotNull();
		assertThat(entity.getEditTime()).isEqualTo(entity.getCreateTime());
	}

	@Test
	void insertFillKeepsExistingValues() {
		LocalDateTime createTime = LocalDateTime.of(2024, 1, 2, 3, 4, 5);
		LocalDateTime editTime = createTime.plusDays(1);
		BaseEntity entity = new BaseEntity();
		entity.setCreateTime(createTime);
		entity.setEditTime(editTime);

		this.handler.insertFill(SystemMetaObject.forObject(entity));

		assertThat(entity.getCreateTime()).isEqualTo(createTime);
		assertThat(entity.getEditTime()).isEqualTo(editTime);
	}

	@Test
	void insertFillDoesNotTouchOperatorFields() {
		BaseEntity entity = new BaseEntity();

		this.handler.insertFill(SystemMetaObject.forObject(entity));

		assertThat(entity.getCreatorId()).isNull();
		assertThat(entity.getEditorId()).isNull();
	}

	@Test
	void updateFillOverwritesEditTime() {
		LocalDateTime editTime = LocalDateTime.of(2020, 1, 1, 0, 0);
		BaseEntity entity = new BaseEntity();
		entity.setEditTime(editTime);

		this.handler.updateFill(SystemMetaObject.forObject(entity));

		assertThat(entity.getEditTime()).isAfter(editTime);
	}

	@Test
	void updateFillDoesNotTouchCreateTime() {
		LocalDateTime createTime = LocalDateTime.of(2020, 1, 1, 0, 0);
		BaseEntity entity = new BaseEntity();
		entity.setCreateTime(createTime);

		this.handler.updateFill(SystemMetaObject.forObject(entity));

		assertThat(entity.getCreateTime()).isEqualTo(createTime);
	}

	@Test
	void ignoresEntitiesWithoutAuditFields() {
		PlainEntity entity = new PlainEntity();

		assertThatCode(() -> this.handler.insertFill(SystemMetaObject.forObject(entity))).doesNotThrowAnyException();
		assertThatCode(() -> this.handler.updateFill(SystemMetaObject.forObject(entity))).doesNotThrowAnyException();
		assertThat(entity.getName()).isNull();
	}

	@Setter
	@Getter
	static class PlainEntity {

		private String name;

	}

}
