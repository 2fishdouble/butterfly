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

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 由 {@link MybatisPlusAutoConfiguration} 以 {@code @Bean} 方式注册,不参与组件扫描.
 */
public class BaseEntityHandler implements MetaObjectHandler {

	/**
	 * 插入时填充审计字段.
	 * <p>
	 * 仅当实体上能解析出 {@code create_time}/{@code edit_time} 对应的属性(即 {@code createTime}/
	 * {@code editTime}),且该属性当前值为 {@code null} 时,才写入同一个当前时间;
	 * 已赋值的字段不会被覆盖,{@code creator_id}/{@code editor_id} 不在此处填充。
	 * @param metaObject 由 MyBatis 包装后的实体元对象
	 */
	@Override
	public void insertFill(MetaObject metaObject) {
		LocalDateTime now = LocalDateTime.now();

		if (findField(metaObject, "create_time") && getFieldValByName("createTime", metaObject) == null) {
			this.setFieldValByName("createTime", now, metaObject);
		}

		if (findField(metaObject, "edit_time") && getFieldValByName("editTime", metaObject) == null) {
			this.setFieldValByName("editTime", now, metaObject);
		}

	}

	/**
	 * 更新时填充修改时间:无条件把 {@code editTime} 覆盖为当前时间,不做是否已赋值的判断.
	 * @param metaObject 由 MyBatis 包装后的实体元对象
	 */
	@Override
	public void updateFill(MetaObject metaObject) {
		this.setFieldValByName("editTime", LocalDateTime.now(), metaObject);
	}

	private boolean findField(MetaObject metaObject, String fieldName) {
		String property = metaObject.getObjectWrapper().findProperty(fieldName, true);
		return Objects.nonNull(property);
	}

}
