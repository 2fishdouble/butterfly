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

package io.github.butterfly.starter;

import io.github.butterfly.autoconfigure.ButterflyAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 校验 starter 的最小依赖场景:没有 spring-web(spring-web 在 butterfly-spring-boot-autoconfigure
 * 里是可选依赖) 时,基础自动配置仍然能完成条件评估.
 * <p>
 * 本模块的编译 classpath 正是"只跑 MQ 或只跑定时任务"这类应用的样子,可以用来复现并守住一条回归:配置类里只要有一个 返回 Web 类型的方法,Spring
 * 在评估 {@code @ConditionalOnMissingBean} 时反射读取方法签名就会抛
 * {@code NoClassDefFoundError: org.springframework.web.filter.OncePerRequestFilter};本模块通过把
 * TraceId 过滤器 放进带 {@code @ConditionalOnClass} 的嵌套配置类解决。若哪天又把 Web 类型的 Bean 方法挪回主配置类,这里会失败。
 */
class ButterflyStarterClasspathTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ButterflyAutoConfiguration.class));

	@Test
	void loadsButterflyAutoConfigurationWithoutSpringWeb() {
		this.contextRunner.run((context) -> {
			assertThat(context).hasNotFailed();
			// 没有 spring-web 时 TraceId 过滤器整体缺席,而不是让上下文起不来
			assertThat(context).doesNotHaveBean("traceIdFilter");
		});
	}

}
