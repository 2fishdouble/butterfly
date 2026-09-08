package io.github.butterfly.sandbox.permission;

import com.baomidou.mybatisplus.annotation.EnumValue;
import io.github.butterfly.security.autoconfigure.IPermissionGroup;


public enum PermissionGroupEnum implements IPermissionGroup {

    PRODUCT(100,"商品管理",100),

    ;

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
        return id;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public int getOrder() {
        return order;
    }
}
