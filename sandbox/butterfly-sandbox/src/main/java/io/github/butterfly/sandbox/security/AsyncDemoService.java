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

import io.github.butterfly.security.autoconfigure.authentication.UserContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * 异步方法示例:在 {@code @Async} 线程里读 {@link UserContext}.
 * <p>
 * 能读到「提交任务的那个用户」,靠的是执行器上挂的
 * {@link io.github.butterfly.security.autoconfigure.authentication.UserContextTaskDecorator}
 * (内部用 TransmittableThreadLocal 的 capture/replay/restore)。
 */
@Service
public class AsyncDemoService {

	/**
	 * 在异步线程里取当前登录名.
	 * @return 异步结果,内容是提交任务那一刻的登录名
	 */
	@Async
	public CompletableFuture<String> currentUsername() {
		return CompletableFuture.completedFuture(UserContext.username());
	}

}
