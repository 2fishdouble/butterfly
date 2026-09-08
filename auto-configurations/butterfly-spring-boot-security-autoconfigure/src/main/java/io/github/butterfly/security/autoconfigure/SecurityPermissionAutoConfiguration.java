package io.github.butterfly.security.autoconfigure;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * 自动收集权限/分组枚举并存储。
 * <ul>
 *     <li>有 {@link DataSource} → {@link DbPermissionStorage} 建表入库;</li>
 *     <li>无数据源 → {@link InMemoryPermissionStorage} 保底;</li>
 *     <li>使用方自定义 {@link PermissionStorage} bean 时直接覆盖。</li>
 * </ul>
 */
@AutoConfiguration
@EnableConfigurationProperties(SecurityPermissionProperties.class)
@ConditionalOnProperty(prefix = "butterfly.security.permission", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SecurityPermissionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(PermissionStorage.class)
    PermissionStorage permissionStorage(SecurityPermissionProperties properties,
                                        ObjectProvider<DataSource> dataSourceProvider) {
        if (properties.isDb()) {
            DataSource dataSource = dataSourceProvider.getIfAvailable();
            if (dataSource != null) {
                return new DbPermissionStorage(dataSource);
            }
        }
        return new InMemoryPermissionStorage();
    }

    @Bean
    @ConditionalOnMissingBean(PermissionCollector.class)
    PermissionCollector permissionCollector(PermissionStorage storage,
                                            SecurityPermissionProperties properties,
                                            ApplicationContext context) {
        return new PermissionCollector(storage, properties, context);
    }
}
