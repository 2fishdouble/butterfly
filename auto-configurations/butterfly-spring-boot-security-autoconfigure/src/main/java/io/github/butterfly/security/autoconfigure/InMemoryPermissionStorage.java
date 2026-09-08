package io.github.butterfly.security.autoconfigure;

import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 内存存储(无数据库环境的保底方案),同时为后续权限校验组件提供按 id 查询入口。
 */
public class InMemoryPermissionStorage implements PermissionStorage {

    private final ConcurrentMap<Long, IPermissionGroup> groups = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, IPermission> permissions = new ConcurrentHashMap<>();

    @Override
    public void store(Collection<? extends IPermissionGroup> groups, Collection<? extends IPermission> permissions) {
        for (IPermissionGroup group : groups) {
            this.groups.put(group.getId(), group);
        }
        for (IPermission permission : permissions) {
            this.permissions.put(permission.getId(), permission);
        }
    }

    @Nullable
    public IPermissionGroup getGroup(long id) {
        return this.groups.get(id);
    }

    @Nullable
    public IPermission getPermission(long id) {
        return this.permissions.get(id);
    }

    public Collection<IPermissionGroup> groups() {
        return Collections.unmodifiableCollection(this.groups.values());
    }

    public Collection<IPermission> permissions() {
        return Collections.unmodifiableCollection(this.permissions.values());
    }
}
