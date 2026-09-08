package io.github.butterfly.security.autoconfigure;

import org.jspecify.annotations.Nullable;

/**
 * 权限。使用方自定义的权限枚举实现本接口即可被自动收集,例如:
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
 * 其中 {@link #getGroup()} 的返回类型可以是实现了 {@link IPermissionGroup} 的具体枚举,
 * {@link #getParent()} 的返回类型可以是实现了 {@link IPermission} 的具体枚举(协变返回)。
 */
public interface IPermission {

    long getId();

    /**
     * 权限名称,入库到 authority.name。
     */
    String getTitle();

    String getDescription();

    /**
     * UI 描述,方便从数据库反查枚举。
     */
    String getUiDescription();

    /**
     * 所属权限分组,入库到 authority.authority_group_id。
     */
    @Nullable
    IPermissionGroup getGroup();

    /**
     * 父级权限(用于勾选联动),入库到 authority.parent_id;无父级返回 {@code null}。
     */
    @Nullable
    IPermission getParent();
}
