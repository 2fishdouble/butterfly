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

import com.alibaba.ttl.TtlRunnable;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link UserContextTaskDecorator} 的传递与清理行为.
 * <p>
 * 用单线程池模拟真实的执行器:装饰器在提交线程捕获 TTL 快照,执行线程里重放,结束后恢复。
 */
class UserContextTaskDecoratorTests {

	private final UserContextTaskDecorator decorator = new UserContextTaskDecorator();

	private final ExecutorService pool = Executors.newSingleThreadExecutor();

	@BeforeEach
	void warmUpWorkerThread() throws Exception {
		// 真实用法里线程池随容器启动创建,那时没有任何请求上下文;这里先跑一个空任务,把线程建出来
		this.pool.submit(() -> {
		}).get();
	}

	@AfterEach
	void tearDown() {
		this.pool.shutdownNow();
		UserContext.clear();
	}

	@Test
	void carriesUserToWorkerThread() throws Exception {
		LoginUser user = new LoginUser(7L, "alice", Set.of(100L));
		UserContext.set(user);
		AtomicReference<@Nullable LoginUser> seen = new AtomicReference<>();

		this.pool.submit(this.decorator.decorate(() -> seen.set(UserContext.require()))).get();

		assertThat(seen.get()).isEqualTo(user);
	}

	@Test
	void workerThreadIsCleanForTheNextTask() throws Exception {
		UserContext.set(new LoginUser(7L, "alice", Set.of(100L)));
		this.pool.submit(this.decorator.decorate(UserContext::require)).get();

		AtomicBoolean presentInNextTask = new AtomicBoolean(true);
		this.pool.submit(() -> presentInNextTask.set(UserContext.isPresent())).get();

		assertThat(presentInNextTask.get()).as("上一个任务的用户不能残留在线程池线程里").isFalse();
	}

	@Test
	void submitsWithoutUserRunWithEmptyContext() throws Exception {
		AtomicBoolean present = new AtomicBoolean(true);

		this.pool.submit(this.decorator.decorate(() -> present.set(UserContext.isPresent()))).get();

		assertThat(present.get()).as("提交线程没有用户时,执行线程也不应该有").isFalse();
	}

	@Test
	void contextIsClearedWhenTaskFails() throws Exception {
		UserContext.set(new LoginUser(7L, "alice", Set.of(100L)));
		Runnable failing = this.decorator.decorate(() -> {
			throw new IllegalStateException("boom");
		});

		assertThatThrownBy(() -> this.pool.submit(failing).get()).isInstanceOf(ExecutionException.class)
			.hasCauseInstanceOf(IllegalStateException.class);

		AtomicBoolean presentInNextTask = new AtomicBoolean(true);
		this.pool.submit(() -> presentInNextTask.set(UserContext.isPresent())).get();
		assertThat(presentInNextTask.get()).isFalse();
	}

	@Test
	void ttlRunnableCarriesUserWithoutThisDecorator() throws Exception {
		UserContext.set(new LoginUser(9L, "bob", Set.of(200L)));
		AtomicReference<@Nullable String> seen = new AtomicReference<>();

		// 直接用 TTL 的包装器(不挂 agent 的另一种用法),证明底层确实是 TransmittableThreadLocal
		this.pool.submit(TtlRunnable.get(() -> seen.set(UserContext.username()))).get();

		assertThat(seen.get()).isEqualTo("bob");
	}

	@Test
	void threadsCreatedWhileUserIsSetInheritIt() throws Exception {
		// TransmittableThreadLocal 继承自 InheritableThreadLocal:请求期间新建的线程会继承当前用户。
		// 这也是「线程池要随容器启动创建」的原因,测试固定住这个语义,避免以后误以为它会自动清空。
		UserContext.set(new LoginUser(7L, "alice", Set.of(100L)));
		try (ExecutorService freshThreadPool = Executors.newSingleThreadExecutor()) {
			AtomicBoolean present = new AtomicBoolean(false);
			freshThreadPool.submit(() -> present.set(UserContext.isPresent())).get();

			assertThat(present.get()).isTrue();
		}
	}

}
