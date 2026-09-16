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

import org.jspecify.annotations.Nullable;

/**
 * 权限.使用方自定义的权限枚举实现本接口即可被自动收集,例如:
 *
 * <pre>{@code
 * public enum MyPermission implements IPermission {
 *     SYSTEM_PAGE(MyGroup.SYSTEM, 1000000L, "系统管理", "", "系统管理-列表", null),
 *     SYSTEM_USER_LIST(MyGroup.SYSTEM, 1000100L, "用户列表", "", "系统管理-用户列表", SYSTEM_PAGE);
 *
 *     ...
 * }
 * }</pre>
 *
 * 其中 {@link #getGroup()} 的返回类型可以是实现了 {@link IPermissionGroup} 的具体枚举, {@link #getParent()}
 * 的返回类型可以是实现了 {@link IPermission} 的具体枚举(协变返回)。
 */
public interface IPermission {

	/**
	 * 权限 id,入库到 authority.id.
	 * @return 权限的唯一标识
	 */
	long getId();

	/**
	 * 权限名称,入库到 authority.name.
	 * @return 权限名称
	 */
	String getTitle();

	/**
	 * 权限说明,入库到 authority.description.
	 * @return 权限说明,无说明时返回空字符串
	 */
	String getDescription();

	/**
	 * UI 描述,方便从数据库反查枚举.
	 * @return UI 描述
	 */
	String getUiDescription();

	/**
	 * 所属权限分组,入库到 authority.authority_group_id.
	 * @return 所属分组,不属于任何分组时返回 {@code null}
	 */
	@Nullable IPermissionGroup getGroup();

	/**
	 * 父级权限(用于勾选联动),入库到 authority.parent_id;无父级返回 {@code null}.
	 * @return 父级权限,无父级时返回 {@code null}
	 */
	@Nullable IPermission getParent();

}
