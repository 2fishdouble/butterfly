package io.github.butterfly.security.autoconfigure;

public enum SamplePermissionGroup implements IPermissionGroup {

    SYSTEM(1L, "系统管理", 1),
    SALE(2L, "销售管理", 2);

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
