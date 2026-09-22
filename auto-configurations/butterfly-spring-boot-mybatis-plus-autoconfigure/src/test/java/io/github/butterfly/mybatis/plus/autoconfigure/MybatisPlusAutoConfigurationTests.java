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
import org.apache.ibatis.reflection.MetaObject;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 覆盖 MyBatis-Plus 自动配置的 Bean 注册、内部拦截器顺序与让位条件.
 */
class MybatisPlusAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(MybatisPlusAutoConfiguration.class));

	@Test
	void registersPaginationThenOptimisticLockerInterceptor() {
		this.contextRunner.run((context) -> {
			MybatisPlusInterceptor interceptor = context.getBean(MybatisPlusInterceptor.class);

			assertThat(interceptor.getInterceptors()).hasSize(2);
			assertThat(interceptor.getInterceptors().get(0)).isInstanceOf(PaginationInnerInterceptor.class);
			assertThat(interceptor.getInterceptors().get(1)).isInstanceOf(OptimisticLockerInnerInterceptor.class);
		});
	}

	@Test
	void backsOffInterceptorWhenUserDefinesBean() {
		MybatisPlusInterceptor custom = new MybatisPlusInterceptor();

		this.contextRunner.withBean(MybatisPlusInterceptor.class, () -> custom).run((context) -> {
			assertThat(context).hasSingleBean(MybatisPlusInterceptor.class);
			assertThat(context.getBean(MybatisPlusInterceptor.class)).isSameAs(custom);
		});
	}

	@Test
	void registersBaseEntityHandlerAsMetaObjectHandler() {
		this.contextRunner.run((context) -> {
			assertThat(context).hasSingleBean(MetaObjectHandler.class);
			assertThat(context.getBean(MetaObjectHandler.class)).isInstanceOf(BaseEntityHandler.class);
		});
	}

	@Test
	void backsOffMetaObjectHandlerWhenUserDefinesBean() {
		MetaObjectHandler custom = new MetaObjectHandler() {
			@Override
			public void insertFill(MetaObject metaObject) {
			}

			@Override
			public void updateFill(MetaObject metaObject) {
			}
		};

		this.contextRunner.withBean(MetaObjectHandler.class, () -> custom).run((context) -> {
			assertThat(context).hasSingleBean(MetaObjectHandler.class);
			assertThat(context.getBean(MetaObjectHandler.class)).isSameAs(custom);
		});
	}

	@Test
	void skipsEverythingWhenMybatisPlusIsMissing() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(MybatisPlusAutoConfiguration.class))
			.withClassLoader(new FilteredClassLoader(MybatisPlusInterceptor.class))
			.run((context) -> {
				assertThat(context).doesNotHaveBean(MybatisPlusInterceptor.class);
				assertThat(context).doesNotHaveBean(MetaObjectHandler.class);
			});
	}

}
