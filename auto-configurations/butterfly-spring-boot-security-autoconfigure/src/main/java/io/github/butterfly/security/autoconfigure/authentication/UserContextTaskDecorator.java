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

import com.alibaba.ttl.TransmittableThreadLocal.Transmitter;
import org.springframework.core.task.TaskDecorator;

/**
 * 把提交任务那一刻的 {@link UserContext} 带到执行任务的线程里,任务结束后恢复线程原来的状态.
 * <p>
 * {@link UserContext} 的底层是 Alibaba 的 {@code TransmittableThreadLocal},跨线程传递必须配合 TTL 的
 * 「捕获—重放—恢复」机制。本装饰器不需要 agent:在提交线程用 {@link Transmitter#capture()} 抓一份快照, 执行线程里
 * {@link Transmitter#replay(Object)} 换上,结束后 {@link Transmitter#restore(Object)} 还回去,
 * 因此线程池复用不会把用户带到下一个任务。
 *
 * <pre>{@code
 * &#64;Bean
 * ThreadPoolTaskExecutor applicationTaskExecutor(UserContextTaskDecorator userContextTaskDecorator) {
 *     ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
 *     executor.setTaskDecorator(userContextTaskDecorator);
 *     executor.initialize();
 *     return executor;
 * }
 * }</pre>
 * <p>
 * 只建议挂到「代表当前用户执行」的线程池上(异步发消息、异步写审计等),不要挂到定时任务或全局共享池:那样会让
 * 后台任务莫名以某个用户的身份执行。这也是框架不自动包装任何执行器的原因。
 * <p>
 * 如果应用已经给 JVM 挂了 TTL 的 agent,{@code ThreadPoolExecutor} 里的任务会被自动包装,本装饰器就不是必需的
 * (重复包装也没有副作用,只是多一层)。
 */
public class UserContextTaskDecorator implements TaskDecorator {

	/**
	 * 捕获当前线程的 TTL 快照,返回一个执行前重放、执行后恢复的包装任务.
	 * @param runnable 原始任务
	 * @return 带上下文传递的包装任务
	 */
	@Override
	public Runnable decorate(Runnable runnable) {
		Object captured = Transmitter.capture();
		return () -> {
			Object backup = Transmitter.replay(captured);
			try {
				runnable.run();
			}
			finally {
				Transmitter.restore(backup);
			}
		};
	}

}
