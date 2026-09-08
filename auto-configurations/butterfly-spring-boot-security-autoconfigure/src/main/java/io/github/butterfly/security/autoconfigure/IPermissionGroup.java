package io.github.butterfly.security.autoconfigure;

/**
 * 权限分组。
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

    long getId();

    String getTitle();

    int getOrder();
}
