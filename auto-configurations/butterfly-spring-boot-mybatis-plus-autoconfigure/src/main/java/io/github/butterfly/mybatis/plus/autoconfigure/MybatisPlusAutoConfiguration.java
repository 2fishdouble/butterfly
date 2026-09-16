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

package io.github.butterfly.mybatis.plus.autoconfigure;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * MyBatis-Plus 自动配置:装配分页插件、乐观锁插件与审计字段自动填充处理器.
 * <p>
 * 仅当类路径存在 {@link MybatisPlusInterceptor} 时生效;两类 bean 均在使用方已自行定义时让位。
 */
@AutoConfiguration
@ConditionalOnClass(MybatisPlusInterceptor.class)
public class MybatisPlusAutoConfiguration {

	/**
	 * 注册 MyBatis-Plus 拦截器,依次加入分页插件 {@link PaginationInnerInterceptor} 与乐观锁插件
	 * {@link OptimisticLockerInnerInterceptor};两者的顺序即为内部拦截器的执行顺序.
	 * @return 已装配上述内部拦截器的 {@link MybatisPlusInterceptor} 实例
	 */
	@Bean
	@ConditionalOnMissingBean(MybatisPlusInterceptor.class)
	public MybatisPlusInterceptor mybatisPlusInterceptor() {
		MybatisPlusInterceptor mpi = new MybatisPlusInterceptor();
		mpi.addInnerInterceptor(new PaginationInnerInterceptor());
		mpi.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
		return mpi;
	}

	/**
	 * 审计字段(createTime/editTime)自动填充.
	 * <p>
	 * 使用方自定义 {@link MetaObjectHandler} 时自动让位;不再通过 {@code AutoConfiguration.imports} 注册
	 * {@code @Component},避免把普通 Bean 当配置类处理。
	 * @return 使用默认审计字段填充逻辑的 {@link BaseEntityHandler} 实例
	 */
	@Bean
	@ConditionalOnMissingBean(MetaObjectHandler.class)
	public MetaObjectHandler baseEntityHandler() {
		return new BaseEntityHandler();
	}

}
