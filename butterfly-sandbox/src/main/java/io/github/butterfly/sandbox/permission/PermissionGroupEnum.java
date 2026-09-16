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

package io.github.butterfly.sandbox.permission;

import com.baomidou.mybatisplus.annotation.EnumValue;
import io.github.butterfly.security.autoconfigure.IPermissionGroup;

/**
 * 权限分组枚举.
 * <p>
 * 定义权限项所属的分组,用于前端按分组展示与勾选权限.
 */
public enum PermissionGroupEnum implements IPermissionGroup {

	/**
	 * 商品管理分组.
	 */
	PRODUCT(100, "商品管理", 100);

	@EnumValue
	private final int id;

	private final String title;

	private final int order;

	PermissionGroupEnum(int id, String title, int order) {
		this.id = id;
		this.title = title;
		this.order = order;
	}

	@Override
	public long getId() {
		return this.id;
	}

	@Override
	public String getTitle() {
		return this.title;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

}
