package io.github.butterfly.security.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.ClassUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 在容器刷新完成后,扫描指定包下实现了 {@link IPermission} / {@link IPermissionGroup} 的枚举,
 * 取其常量并交给 {@link PermissionStorage} 落库(有 DataSource)或入内存。
 */
public class PermissionCollector implements ApplicationListener<ContextRefreshedEvent> {

    private static final Logger log = LoggerFactory.getLogger(PermissionCollector.class);

    private final PermissionStorage storage;
    private final SecurityPermissionProperties properties;
    private final ApplicationContext context;
    private final AtomicBoolean collected = new AtomicBoolean(false);

    public PermissionCollector(PermissionStorage storage, SecurityPermissionProperties properties, ApplicationContext context) {
        this.storage = storage;
        this.properties = properties;
        this.context = context;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (!this.collected.compareAndSet(false, true)) {
            return;
        }

        List<String> packages = resolveBasePackages();
        if (packages.isEmpty()) {
            log.warn("未配置 butterfly.security.permission.scan-packages,且无法推断启动类包,跳过权限收集");
            return;
        }

        List<IPermissionGroup> groups = new ArrayList<>();
        List<IPermission> permissions = new ArrayList<>();
        scan(packages, groups, permissions);

        if (groups.isEmpty() && permissions.isEmpty()) {
            log.info("在包 {} 下未发现实现 IPermission/IPermissionGroup 的枚举", packages);
            return;
        }

        this.storage.store(groups, permissions);
        log.info("权限收集完成:分组 {} 个,权限 {} 个,存储类型 {}", groups.size(), permissions.size(),
                this.storage.getClass().getSimpleName());
    }

    private List<String> resolveBasePackages() {
        if (!this.properties.getScanPackages().isEmpty()) {
            return this.properties.getScanPackages();
        }
        if (AutoConfigurationPackages.has(this.context)) {
            return new ArrayList<>(AutoConfigurationPackages.get(this.context));
        }
        return List.of();
    }

    private void scan(List<String> basePackages, List<IPermissionGroup> groups, List<IPermission> permissions) {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.setResourceLoader(this.context);
        scanner.addIncludeFilter(new AssignableTypeFilter(IPermission.class));
        scanner.addIncludeFilter(new AssignableTypeFilter(IPermissionGroup.class));

        for (String basePackage : basePackages) {
            for (BeanDefinition candidate : scanner.findCandidateComponents(basePackage)) {
                String className = candidate.getBeanClassName();
                if (className == null) {
                    continue;
                }
                Class<?> type;
                try {
                    type = ClassUtils.forName(className, this.context.getClassLoader());
                } catch (ClassNotFoundException ex) {
                    log.debug("无法加载候选类 {},跳过", className);
                    continue;
                }
                if (!type.isEnum()) {
                    continue;
                }
                if (IPermission.class.isAssignableFrom(type)) {
                    collectPermissionConstants(type, permissions);
                } else if (IPermissionGroup.class.isAssignableFrom(type)) {
                    collectGroupConstants(type, groups);
                }
            }
        }
    }

    private void collectPermissionConstants(Class<?> type, List<IPermission> permissions) {
        for (Object constant : type.getEnumConstants()) {
            if (constant instanceof IPermission permission) {
                permissions.add(permission);
            }
        }
    }

    private void collectGroupConstants(Class<?> type, List<IPermissionGroup> groups) {
        for (Object constant : type.getEnumConstants()) {
            if (constant instanceof IPermissionGroup group) {
                groups.add(group);
            }
        }
    }
}
