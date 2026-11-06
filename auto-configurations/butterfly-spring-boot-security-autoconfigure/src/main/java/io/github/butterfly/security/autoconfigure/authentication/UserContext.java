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

import com.alibaba.ttl.TransmittableThreadLocal;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.Set;

/**
 * 当前登录用户的线程上下文,让任意位置的业务代码都能拿到「谁在调用」.
 *
 * <pre>{@code
 * LoginUser user = UserContext.require();
 * long userId = user.userId();
 * }</pre>
 * <p>
 * 底层用的是 Alibaba {@link TransmittableThreadLocal}:同一线程内读写和普通 {@code ThreadLocal} 完全一致,
 * 另外支持「提交任务时捕获、执行时恢复」的跨线程传递。传递不是自动发生的,两种打开方式:
 * <ul>
 * <li>把本模块的 {@link UserContextTaskDecorator} 挂到执行器上(内部用 TTL 的
 * {@code Transmitter.capture/replay/restore}),不需要给 JVM 加参数,推荐;</li>
 * <li>或者用 TTL 的 {@code TtlRunnable}/{@code TtlCallable}/{@code TtlExecutors} 包装任务,或给应用挂上
 * {@code -javaagent:transmittable-thread-local.jar}。</li>
 * </ul>
 * <p>
 * 由于 {@code TransmittableThreadLocal} 继承自
 * {@code InheritableThreadLocal},<b>请求期间新建</b>的线程会继承 当前用户(TTL
 * 的既定语义)。所以线程池应当随容器启动创建(那时还没有任何请求上下文),不要在处理请求的过程中 临时 {@code new Thread}。
 * <p>
 * 写入与清理由 {@link UserContextFilter} 负责:请求进入时解析 JWT 并放入,请求结束(含异常)时在 {@code finally}
 * 里清理,不会把上一个请求的用户残留到线程池里的下一个请求。
 * <p>
 * 没有登录(匿名访问的接口、定时任务、启动阶段)时 {@link #get()} 为空,{@link #require()} 会抛
 * {@link IllegalStateException}。
 */
public final class UserContext {

	/**
	 * 使用 TTL 默认构造器.
	 * <p>
	 * 类型参数标 {@code @Nullable} 是关键:{@code TransmittableThreadLocal#get()} 在没有值时返回
	 * {@code null},把它声明成非空会让 {@link #isPresent()} 的判空变成「恒真」,检查器也会认为 {@link #require()}
	 * 里的判空是死代码。
	 * <p>
	 * {@code TransmittableThreadLocal(boolean)} 的参数是「是否禁用忽略 null 的语义」,与继承无关,这里不需要。
	 */
	private static final TransmittableThreadLocal<@Nullable LoginUser> HOLDER = new TransmittableThreadLocal<>();

	private static final String NO_USER_MESSAGE = "当前线程没有登录用户: 该接口是否被匿名访问, 或代码是否跑在异步线程/定时任务里?";

	private UserContext() {
	}

	/**
	 * 当前线程是否有登录用户.
	 * @return 有登录用户时返回 {@code true}
	 */
	public static boolean isPresent() {
		return HOLDER.get() != null;
	}

	/**
	 * 取当前登录用户,没有时返回 {@code null}.
	 * @return 当前登录用户,未登录时为 {@code null}
	 */
	public static @Nullable LoginUser get() {
		return HOLDER.get();
	}

	/**
	 * 取当前登录用户,没有时抛出异常,适合「这个接口一定需要登录」的场景.
	 * @return 当前登录用户,不为 {@code null}
	 * @throws IllegalStateException 当前线程没有登录用户时抛出
	 */
	public static LoginUser require() {
		return optional().orElseThrow(() -> new IllegalStateException(NO_USER_MESSAGE));
	}

	/**
	 * 当前用户 id,等价于 {@code require().userId()}.
	 * @return 用户 id
	 * @throws IllegalStateException 当前线程没有登录用户时抛出
	 */
	public static long userId() {
		return require().userId();
	}

	/**
	 * 当前登录名,等价于 {@code require().username()}.
	 * @return 登录名
	 * @throws IllegalStateException 当前线程没有登录用户时抛出
	 */
	public static String username() {
		return require().username();
	}

	/**
	 * 当前用户的权限 id 集合,等价于 {@code require().permissionIds()}.
	 * @return 权限 id 集合,可能为空集合
	 * @throws IllegalStateException 当前线程没有登录用户时抛出
	 */
	public static Set<Long> permissionIds() {
		return require().permissionIds();
	}

	/**
	 * 写入当前登录用户.
	 * <p>
	 * 正常流程只由 {@link UserContextFilter} 调用;需要在自定义过滤器、测试里手工设置时也可以直接用。
	 * @param user 当前登录用户
	 */
	public static void set(LoginUser user) {
		HOLDER.set(user);
	}

	/**
	 * 清理当前线程的登录用户,必须在用完之后调用(过滤器里放在 {@code finally}).
	 */
	public static void clear() {
		HOLDER.remove();
	}

	private static Optional<LoginUser> optional() {
		return Optional.ofNullable(HOLDER.get());
	}

}
