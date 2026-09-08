package io.github.butterfly.sandbox.permission;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import io.github.butterfly.security.autoconfigure.IPermission;
import io.github.butterfly.security.autoconfigure.IPermissionGroup;
import org.jspecify.annotations.Nullable;


public enum PermissionEnum implements IPermission {
    PRODUCT_PAGE_LIST(PermissionGroupEnum.PRODUCT, 1010100, "药品列表", "", "商品管理-商品列表", null),
    PRODUCT_ADD(PermissionGroupEnum.PRODUCT, 1010101, "新增", "", "商品管理-商品列表-新增商品", PRODUCT_PAGE_LIST),
    PRODUCT_EDIT(PermissionGroupEnum.PRODUCT, 1010102, "编辑", "", "商品管理-商品列表-编辑商品", PRODUCT_PAGE_LIST),

    ;

    PermissionEnum(PermissionGroupEnum group, long id, String title, String description, String uiDescription, @Nullable PermissionEnum parent) {
        this.group = group;
        this.id = id;
        this.title = title;
        this.description = description;
        this.uiDescription = uiDescription;
        this.parent = parent;
    }

    /**
     * 权限分组，用于UI界面展示
     * 方便用户勾选和配置
     */
    private final PermissionGroupEnum group;

    /**
     * 权限码
     * 1010101
     * 第一位数字表示是端
     * 第二三位表示控制器
     * 第四五位表示父级权限
     * 第六七位表示子级权限
     */
    @EnumValue
    @JsonValue
    private final long id;

    /**
     * 权限名称，展示给客户使用的权限名称
     */
    private final String title;


    /**
     * 权限描述
     * 前端展示说明的时候使用
     */
    private final String description;

    /**
     * UI描述
     * 方便从数据库中查找对应的数据
     */
    private final String uiDescription;

    /**
     * 父级权限 对应的权限信息
     * 可以为空代表其本身就是根节点权限
     */
    @Nullable
    private final PermissionEnum parent;


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
    public IPermissionGroup getGroup() {
        return this.group;
    }

    @Override
    @Nullable
    public IPermission getParent() {
        return this.parent;
    }
}
