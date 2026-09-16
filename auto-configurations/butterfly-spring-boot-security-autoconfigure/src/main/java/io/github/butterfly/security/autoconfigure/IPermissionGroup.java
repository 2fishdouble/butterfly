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

/**
 * 权限分组.
 * <p>
 * 使用方自定义的分组枚举实现本接口即可被自动收集并入库/入内存,例如:
 *
 * <pre>{@code
 * public enum MyGroup implements IPermissionGroup {
 *     SYSTEM(1L, "系统管理", 1);
 *
 *     MyGroup(long id, String title, int order) { ... }
 * }
 * }</pre>
 */
public interface IPermissionGroup {

	/**
	 * 分组 id,入库到 authority_group.id.
	 * @return 分组的唯一标识
	 */
	long getId();

	/**
	 * 分组名称,入库到 authority_group.name.
	 * @return 分组名称
	 */
	String getTitle();

	/**
	 * 分组排序值,入库到 authority_group.order,值越小越靠前.
	 * @return 排序值
	 */
	int getOrder();

}
