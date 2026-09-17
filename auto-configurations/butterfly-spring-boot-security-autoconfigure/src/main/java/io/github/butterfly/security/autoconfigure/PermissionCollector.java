/*
 * Copyright 2012-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
 * 在容器刷新完成后,扫描指定包下实现了 {@link IPermission} / {@link IPermissionGroup} 的枚举, 取其常量并交给
 * {@link PermissionStorage} 落库(有 DataSource)或入内存.
 */
public class PermissionCollector implements ApplicationListener<ContextRefreshedEvent> {

	private static final Logger log = LoggerFactory.getLogger(PermissionCollector.class);

	private final PermissionStorage storage;

	private final SecurityPermissionProperties properties;

	private final ApplicationContext context;

	private final AtomicBoolean collected = new AtomicBoolean(false);

	/**
	 * 创建权限收集器.
	 * @param storage 收集结果的存储策略
	 * @param properties 权限收集配置,决定扫描包
	 * @param context 用于扫描候选组件与加载类的应用上下文
	 */
	public PermissionCollector(PermissionStorage storage, SecurityPermissionProperties properties,
			ApplicationContext context) {
		this.storage = storage;
		this.properties = properties;
		this.context = context;
	}

	/**
	 * 容器刷新完成后执行一次权限收集,重复事件直接忽略({@link java.util.concurrent.atomic.AtomicBoolean}
	 * 保证仅执行一次).
	 * <p>
	 * 扫描包取 {@code butterfly.security.permission.scan-packages};为空时回退到
	 * {@link AutoConfigurationPackages} 记录的 自动配置包(即 {@code @SpringBootApplication}
	 * 所在包)。未找到实现 {@link IPermission}/{@link IPermissionGroup} 的枚举时只记录日志、不调用存储.
	 * @param event 容器刷新事件
	 */
	@Override
	public void onApplicationEvent(ContextRefreshedEvent event) {
		if (!this.collected.compareAndSet(false, true)) {
			return;
		}

		List<String> packages = resolveBasePackages();
		if (packages.isEmpty()) {
			log.warn(
					"There are no configured butterfly.security.permission.scan-packages, and no auto-configuration package found, skipping permission collection.");
			return;
		}

		List<IPermissionGroup> groups = new ArrayList<>();
		List<IPermission> permissions = new ArrayList<>();
		scan(packages, groups, permissions);

		if (groups.isEmpty() && permissions.isEmpty()) {
			log.info("There are no IPermission/IPermissionGroup enums in the packages {}.", packages);
			return;
		}

		this.storage.store(groups, permissions);
		log.info("Permission collection completed: {} groups, {} permissions, storage type {}.", groups.size(),
				permissions.size(), this.storage.getClass().getSimpleName());
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
				}
				catch (ClassNotFoundException ex) {
					log.debug("Unable to load candidate class {}, skipping.", className);
					continue;
				}
				if (!type.isEnum()) {
					continue;
				}
				if (IPermission.class.isAssignableFrom(type)) {
					collectPermissionConstants(type, permissions);
				}
				else if (IPermissionGroup.class.isAssignableFrom(type)) {
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
