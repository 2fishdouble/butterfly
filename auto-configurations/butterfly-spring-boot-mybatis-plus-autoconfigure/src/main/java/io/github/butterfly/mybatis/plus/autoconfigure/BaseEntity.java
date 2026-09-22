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
import lombok.Data;
import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;

/**
 * 实体基类,供使用方实体继承以获得统一的审计字段与逻辑删除字段.
 * <p>
 * 字段列名与填充时机由 {@link TableField} 声明:{@code create_time} 仅在插入时填充, {@code edit_time}
 * 在插入与更新时填充,实际赋值由 {@link BaseEntityHandler} 完成; {@code is_deleted} 由 {@link TableLogic}
 * 标记为逻辑删除字段。
 */
@Data
public class BaseEntity {

	/**
	 * 创建时间,对应列 {@code create_time};声明为插入时填充,由 {@link BaseEntityHandler} 写入当前时间.
	 */
	@TableField(value = "create_time", fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	/**
	 * 创建人 id,对应列 {@code creator_id};声明为插入时填充,但 {@link BaseEntityHandler} 未实现该字段的
	 * 赋值,需使用方自行设置或注册自定义的 {@code MetaObjectHandler}.
	 */
	@TableField(value = "creator_id", fill = FieldFill.INSERT)
	private @Nullable Long creatorId;

	/**
	 * 最后修改时间,对应列 {@code edit_time};声明为插入与更新时填充,由 {@link BaseEntityHandler} 写入当前时间
	 * (插入时字段已有值则不覆盖,更新时无条件覆盖).
	 */
	@TableField(value = "edit_time", fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime editTime;

	/**
	 * 最后修改人 id,对应列 {@code editor_id};声明为插入与更新时填充,但 {@link BaseEntityHandler} 未实现该字段
	 * 的赋值,需使用方自行设置或注册自定义的 {@code MetaObjectHandler}.
	 */
	@TableField(value = "editor_id", fill = FieldFill.INSERT_UPDATE)
	private @Nullable Long editorId;

	/**
	 * 逻辑删除标记,对应列 {@code is_deleted};标注 {@link TableLogic} 后,删除操作会改为更新该字段,
	 * 查询也会自动追加未删除的过滤条件.
	 */
	@TableField("is_deleted")
	@TableLogic
	private Boolean isDeleted;

}
