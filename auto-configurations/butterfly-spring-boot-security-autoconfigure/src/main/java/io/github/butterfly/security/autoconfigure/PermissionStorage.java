package io.github.butterfly.security.autoconfigure;

import java.util.Collection;

/**
 * 权限收集结果的落库策略(SPI)。
 * <ul>
 *     <li>无数据库环境 → {@link InMemoryPermissionStorage} 保底;</li>
 *     <li>有 {@code DataSource} → {@link DbPermissionStorage} 自动建表入库;</li>
 *     <li>使用方也可自定义实现并声明为 {@code @Bean},将覆盖框架默认存储。</li>
 * </ul>
 */
public interface PermissionStorage {

    /**
     * 存储(合并)一批权限分组与权限。同一 id 以新值覆盖旧值。
     */
    void store(Collection<? extends IPermissionGroup> groups, Collection<? extends IPermission> permissions);
}
