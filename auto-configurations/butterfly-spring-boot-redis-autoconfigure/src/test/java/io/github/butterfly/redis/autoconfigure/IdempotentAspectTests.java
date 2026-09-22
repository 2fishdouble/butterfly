package io.github.butterfly.redis.autoconfigure;

import io.github.butterfly.autoconfigure.SpelSup;
import io.github.butterfly.core.BusinessException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 覆盖幂等切面的加锁、锁 key 拼接、抢锁失败与解锁分支.
 * <p>
 * Redisson 客户端、{@code RLock} 与连接点均以 JDK 动态代理替身实现,不依赖真实 Redis。
 */
class IdempotentAspectTests {

	private final SpelSup spelSup = new SpelSup(new DefaultListableBeanFactory());

	@Test
	void proceedsAndUnlocksWhenLockIsAcquired() throws Throwable {
		FakeLock lock = new FakeLock(true, true);
		IdempotentAspect aspect = new IdempotentAspect(this.spelSup, redisson(lock));

		Object result = aspect.interceptor(joinPoint(method("handle"), new Object[] { "1" }, "handled", null));

		assertThat(result).isEqualTo("handled");
		assertThat(lock.key).hasValue(Service.class.getName() + ".handle");
		assertThat(lock.unlocked).isTrue();
	}

	@Test
	void appendsSpelResultToLockKey() throws Throwable {
		FakeLock lock = new FakeLock(true, true);
		IdempotentAspect aspect = new IdempotentAspect(this.spelSup, redisson(lock));

		aspect.interceptor(joinPoint(method("handleWithKey"), new Object[] { "42" }, "handled", null));

		assertThat(lock.key).hasValue(Service.class.getName() + ".handleWithKey#42");
	}

	@Test
	void failsWhenLockIsNotAcquired() throws Throwable {
		FakeLock lock = new FakeLock(false, false);
		IdempotentAspect aspect = new IdempotentAspect(this.spelSup, redisson(lock));

		assertThatThrownBy(() -> aspect.interceptor(joinPoint(method("handle"), new Object[] { "1" }, "handled", null)))
			.isInstanceOf(BusinessException.class)
			.hasMessage("请稍后再试");
		assertThat(lock.unlocked).isFalse();
	}

	@Test
	void proceedsWithoutLockWhenAnnotationIsMissing() throws Throwable {
		FakeLock lock = new FakeLock(true, true);
		IdempotentAspect aspect = new IdempotentAspect(this.spelSup, redisson(lock));

		Object result = aspect.interceptor(joinPoint(method("plain"), new Object[] { "1" }, "plain", null));

		assertThat(result).isEqualTo("plain");
		assertThat(lock.key).hasValue(null);
	}

	@Test
	void doesNotUnlockWhenLockIsNotHeldByCurrentThread() throws Throwable {
		FakeLock lock = new FakeLock(true, false);
		IdempotentAspect aspect = new IdempotentAspect(this.spelSup, redisson(lock));

		aspect.interceptor(joinPoint(method("handle"), new Object[] { "1" }, "handled", null));

		assertThat(lock.unlocked).isFalse();
	}

	@Test
	void unlocksWhenTargetMethodThrows() throws Throwable {
		FakeLock lock = new FakeLock(true, true);
		IdempotentAspect aspect = new IdempotentAspect(this.spelSup, redisson(lock));
		IllegalStateException failure = new IllegalStateException("boom");

		assertThatThrownBy(() -> aspect.interceptor(joinPoint(method("handle"), new Object[] { "1" }, null, failure)))
			.isSameAs(failure);
		assertThat(lock.unlocked).isTrue();
	}

	private static Method method(String name) throws NoSuchMethodException {
		return Service.class.getDeclaredMethod(name, String.class);
	}

	private static RedissonClient redisson(FakeLock lock) {
		return (RedissonClient) Proxy.newProxyInstance(RedissonClient.class.getClassLoader(),
				new Class<?>[] { RedissonClient.class }, (proxy, invoked, args) -> {
					if ("getLock".equals(invoked.getName())) {
						lock.key.set((String) args[0]);
						return lock.rlock();
					}
					return defaultValue(invoked.getReturnType());
				});
	}

	private static ProceedingJoinPoint joinPoint(Method method, Object[] args, Object result, Throwable failure) {
		MethodSignature signature = (MethodSignature) Proxy.newProxyInstance(
				IdempotentAspectTests.class.getClassLoader(), new Class<?>[] { MethodSignature.class },
				(proxy, invoked, invokedArgs) -> "getMethod".equals(invoked.getName()) ? method
						: defaultValue(invoked.getReturnType()));

		return (ProceedingJoinPoint) Proxy.newProxyInstance(IdempotentAspectTests.class.getClassLoader(),
				new Class<?>[] { ProceedingJoinPoint.class }, (proxy, invoked, invokedArgs) -> {
					switch (invoked.getName()) {
						case "getSignature" -> {
							return signature;
						}
						case "getArgs" -> {
							return args;
						}
						case "proceed" -> {
							if (failure != null) {
								throw failure;
							}
							return result;
						}
						default -> {
							return defaultValue(invoked.getReturnType());
						}
					}
				});
	}

	private static Object defaultValue(Class<?> type) {
		if (!type.isPrimitive() || type == void.class) {
			return null;
		}
		if (type == boolean.class) {
			return Boolean.FALSE;
		}
		if (type == char.class) {
			return (char) 0;
		}
		if (type == byte.class) {
			return (byte) 0;
		}
		if (type == short.class) {
			return (short) 0;
		}
		if (type == int.class) {
			return 0;
		}
		if (type == long.class) {
			return 0L;
		}
		if (type == float.class) {
			return 0F;
		}
		if (type == double.class) {
			return 0D;
		}
		return null;
	}

	static class Service {

		@Idempotent
		String handle(String id) {
			return "handled";
		}

		@Idempotent(lockValue = "#id")
		String handleWithKey(String id) {
			return "handled";
		}

		String plain(String id) {
			return "plain";
		}

	}

	private static final class FakeLock {

		private final AtomicReference<String> key = new AtomicReference<>();

		private final AtomicBoolean unlocked = new AtomicBoolean();

		private final boolean acquired;

		private final boolean heldByCurrentThread;

		private FakeLock(boolean acquired, boolean heldByCurrentThread) {
			this.acquired = acquired;
			this.heldByCurrentThread = heldByCurrentThread;
		}

		private RLock rlock() {
			return (RLock) Proxy.newProxyInstance(RLock.class.getClassLoader(), new Class<?>[] { RLock.class },
					(proxy, invoked, args) -> {
						switch (invoked.getName()) {
							case "tryLock" -> {
								return this.acquired;
							}
							case "isHeldByCurrentThread" -> {
								return this.heldByCurrentThread;
							}
							case "unlock" -> {
								this.unlocked.set(true);
								return null;
							}
							default -> {
								return defaultValue(invoked.getReturnType());
							}
						}
					});
		}

	}

}
