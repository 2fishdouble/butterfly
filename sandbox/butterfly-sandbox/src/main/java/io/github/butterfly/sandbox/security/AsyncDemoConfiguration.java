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

package io.github.butterfly.sandbox.security;

import io.github.butterfly.security.autoconfigure.authentication.UserContextTaskDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 异步执行器示例:把 {@link UserContextTaskDecorator} 挂到线程池上,{@code @Async} 方法里也能读到当前用户.
 * <p>
 * 注意线程池在容器启动阶段就创建(此时没有任何请求上下文),避免 {@code TransmittableThreadLocal} 的
 * 「新线程继承当前值」语义把某个用户的身份带进池子里。
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncDemoConfiguration {

	/**
	 * 供 {@code @Async} 使用的线程池,挂了上下文传递装饰器.
	 * @param userContextTaskDecorator 框架注册的上下文传递装饰器
	 * @return 线程池
	 */
	@Bean
	public ThreadPoolTaskExecutor applicationTaskExecutor(UserContextTaskDecorator userContextTaskDecorator) {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(4);
		executor.setThreadNamePrefix("sandbox-async-");
		executor.setTaskDecorator(userContextTaskDecorator);
		executor.initialize();
		return executor;
	}

}
