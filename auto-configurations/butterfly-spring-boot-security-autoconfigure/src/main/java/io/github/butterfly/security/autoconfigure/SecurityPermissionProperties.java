package io.github.butterfly.security.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限收集配置,前缀 {@code butterfly.security.permission}。
 */
@Data
@ConfigurationProperties(prefix = "butterfly.security.permission")
public class SecurityPermissionProperties {

    /**
     * 总开关,默认开启。
     */
    private boolean enabled = true;

    /**
     * 存在 {@code DataSource} 时是否使用数据库存储(自动建表 + Upsert)。
     * 关闭后无论是否有数据源都退化为内存存储。
     */
    private boolean db = false;

    /**
     * 需要扫描的包(使用方权限/分组枚举所在包)。
     * 为空时回退扫描 {@code @SpringBootApplication} 主类所在包。
     */
    private List<String> scanPackages = new ArrayList<>();
}
