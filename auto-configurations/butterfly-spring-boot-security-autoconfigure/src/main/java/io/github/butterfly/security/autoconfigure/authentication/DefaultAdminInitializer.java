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

package io.github.butterfly.security.autoconfigure.authentication;

import io.github.butterfly.security.autoconfigure.authorization.InMemoryPermissionRepository;
import io.github.butterfly.security.autoconfigure.authorization.PermissionRepository;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.Ordered;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 首次启动时创建默认管理员,让「引依赖就能登录」成立.
 * <p>
 * 只在账号后端是本框架的内存实现时执行:写入 {@code admin} 账号(密码用 BCrypt 加密)并授予全部已收集的权限。 使用方提供自己的
 * {@link UserAccountRepository} 时,账号与密码由业务自己初始化,本类直接跳过、不写入任何数据; 内存后端已有账号时也不会覆盖。
 * <p>
 * 监听容器刷新事件并且在权限收集之后执行({@link Ordered#LOWEST_PRECEDENCE}), 这样内存授权里已经有可授予的权限字典;重复的刷新事件会被忽略。
 * <p>
 * 生产环境请修改 {@code butterfly.security.authentication.default-admin-password},或直接把
 * {@code init-default-admin} 设为 {@code false} 并自行初始化账号。
 */
public class DefaultAdminInitializer implements ApplicationListener<ContextRefreshedEvent>, Ordered {

	/**
	 * 默认管理员固定的用户 id.
	 */
	public static final long DEFAULT_ADMIN_ID = 1L;

	private static final String DEFAULT_ADMIN_NICKNAME = "超级管理员";

	private static final Logger log = LoggerFactory.getLogger(DefaultAdminInitializer.class);

	private final UserAccountRepository userAccounts;

	private final PasswordEncoder passwordEncoder;

	private final @Nullable InMemoryPermissionRepository inMemoryPermissions;

	private final String username;

	private final String password;

	private final AtomicBoolean initialized = new AtomicBoolean(false);

	/**
	 * 创建默认管理员初始化器.
	 * @param userAccounts 账号读取入口,只有内存实现会被写入
	 * @param passwordEncoder 密码编码器,用于生成 BCrypt 散列
	 * @param permissionRepository 权限授予关系实现,只有内存实现会在初始化时被直接授予全部权限
	 * @param properties 认证配置,提供默认管理员的登录名与初始密码
	 */
	public DefaultAdminInitializer(UserAccountRepository userAccounts, PasswordEncoder passwordEncoder,
			@Nullable PermissionRepository permissionRepository, SecurityAuthenticationProperties properties) {
		this.userAccounts = userAccounts;
		this.passwordEncoder = passwordEncoder;
		this.inMemoryPermissions = (permissionRepository instanceof InMemoryPermissionRepository memory) ? memory
				: null;
		this.username = properties.getDefaultAdminUsername();
		this.password = properties.getDefaultAdminPassword();
	}

	/**
	 * 在权限收集之后执行,保证在其它监听器之前拿到完整的权限字典.
	 * @return 最低优先级,排在权限收集器之后
	 */
	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

	/**
	 * 容器刷新完成后,若账号后端是内存实现且没有任何账号,则创建默认管理员并授予全部权限.
	 * @param event 容器刷新事件
	 */
	@Override
	public void onApplicationEvent(ContextRefreshedEvent event) {
		if (!this.initialized.compareAndSet(false, true)) {
			return;
		}
		if (!(this.userAccounts instanceof InMemoryUserAccountRepository inMemory)) {
			log.info("检测到自定义 UserAccountRepository,跳过默认管理员初始化,账号与密码由业务自行准备。");
			return;
		}
		if (inMemory.count() > 0) {
			return;
		}
		UserAccount admin = new UserAccount(DEFAULT_ADMIN_ID, this.username, this.passwordEncoder.encode(this.password),
				DEFAULT_ADMIN_NICKNAME, true);
		inMemory.save(admin);
		grantAllPermissions(this.inMemoryPermissions);
		if (StringUtils.hasText(this.password)) {
			log.warn("已创建默认管理员 [{}], 请尽快修改 butterfly.security.authentication.default-admin-password " + "并重置密码散列。",
					this.username);
		}
		else {
			log.warn("已创建默认管理员 [{}], 其密码为空, 请立即在配置中设置 default-admin-password。", this.username);
		}
	}

	/**
	 * 给默认管理员授予全部已收集的权限.
	 * <p>
	 * 只有内存授权实现能在这里直接授予;数据库实现或其他自定义实现由各自的查询逻辑负责, 因此参数可空, 可空值只作为参数进来,方法体里不需要可空的局部变量。
	 * @param permissions 内存授权实现,可能为 {@code null}
	 */
	private static void grantAllPermissions(@Nullable InMemoryPermissionRepository permissions) {
		if (permissions != null) {
			permissions.grantAll(DEFAULT_ADMIN_ID);
		}
	}

}
