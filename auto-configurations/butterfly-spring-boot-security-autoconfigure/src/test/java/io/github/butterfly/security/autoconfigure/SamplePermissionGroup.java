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

package io.github.butterfly.security.autoconfigure;

public enum SamplePermissionGroup implements IPermissionGroup {

	SYSTEM(1L, "系统管理", 1), SALE(2L, "销售管理", 2);

	private final long id;

	private final String title;

	private final int order;

	SamplePermissionGroup(long id, String title, int order) {
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
