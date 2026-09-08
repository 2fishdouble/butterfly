package io.github.butterfly.security.autoconfigure;

import org.jspecify.annotations.Nullable;

public enum SamplePermission implements IPermission {

    SYSTEM_PAGE(SamplePermissionGroup.SYSTEM, 100L, "系统管理", "", "系统管理-列表", null),
    SYSTEM_USER_ADD(SamplePermissionGroup.SYSTEM, 101L, "新增用户", "新增一个用户", "系统管理-新增用户", SYSTEM_PAGE),
    SALE_PAGE(SamplePermissionGroup.SALE, 200L, "销售管理", "", "销售管理-列表", null),
    SALE_EXPORT(SamplePermissionGroup.SALE, 201L, "导出", "导出销售数据", "销售管理-导出", SALE_PAGE);

    private final SamplePermissionGroup group;
    private final long id;
    private final String title;
    private final String description;
    private final String uiDescription;
    @Nullable
    private final SamplePermission parent;

    SamplePermission(SamplePermissionGroup group, long id, String title,
                     String description, String uiDescription, @Nullable SamplePermission parent) {
        this.group = group;
        this.id = id;
        this.title = title;
        this.description = description;
        this.uiDescription = uiDescription;
        this.parent = parent;
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
    public String getDescription() {
        return this.description;
    }

    @Override
    public String getUiDescription() {
        return this.uiDescription;
    }

    @Override
    public SamplePermissionGroup getGroup() {
        return this.group;
    }

    @Override
    @Nullable
    public SamplePermission getParent() {
        return this.parent;
    }
}
