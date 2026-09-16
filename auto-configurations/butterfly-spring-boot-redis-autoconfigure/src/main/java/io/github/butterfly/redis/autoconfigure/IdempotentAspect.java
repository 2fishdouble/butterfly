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

package io.github.butterfly.redis.autoconfigure;

import cn.hutool.core.lang.Validator;
import io.github.butterfly.autoconfigure.SpelSup;
import io.github.butterfly.core.BusinessException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 幂等切面:为标注了 {@link Idempotent} 的方法提供基于 Redisson 分布式锁的幂等控制。
 * <p>
 * 由 {@link ButterflyRedisConfiguration} 以 {@code @Bean} 方式注册,不参与组件扫描。
 * <p>
 * 拦截逻辑为:按「方法所属类全名 + '.' + 方法名」构造锁 key 前缀,拼接由 {@link Idempotent#lockValue()} 解析出的 SpEL
 * 结果(非空时以 {@code "#"} 连接)后,以 {@code RLock.tryLock(0, -1, TimeUnit.SECONDS)} 立即
 * 尝试加锁——不等待、不设置自动过期;抢锁失败抛出 {@link io.github.butterfly.core.BusinessException}(消息为
 * {@code 请稍后再试}),抢锁成功则执行目标方法, 并在 {@code finally} 中只由持锁线程解锁。目标方法上没有 {@link Idempotent}
 * 注解时直接放行。
 */
@Aspect
public class IdempotentAspect {

	private final RedissonClient redissonClient;

	private final SpelSup spelSup;

	/**
	 * 创建幂等切面.
	 * @param spelSup 用于解析 {@link Idempotent#lockValue()} 中 SpEL 表达式的支持组件
	 * @param redissonClient 用于获取分布式锁的 Redisson 客户端
	 */
	public IdempotentAspect(SpelSup spelSup, RedissonClient redissonClient) {
		this.spelSup = spelSup;
		this.redissonClient = redissonClient;
	}

	/**
	 * 幂等切点:匹配所有标注了 {@link Idempotent} 的方法.
	 * <p>
	 * 方法体为空且返回类型为 {@code void},仅供 {@link #interceptor(ProceedingJoinPoint)} 上的
	 * {@code @Around} 通知按名称引用,不会被业务代码调用。
	 */
	@Pointcut("@annotation(io.github.butterfly.redis.autoconfigure.Idempotent)")
	public void cut() {
	}

	/**
	 * 环绕通知:对标注了 {@link Idempotent} 的目标方法先抢锁再执行,以此保证幂等.
	 * <p>
	 * 若从目标方法上取不到 {@link Idempotent} 注解,则不参与加锁,直接执行并返回目标方法的结果。抢锁失败时 不执行目标方法,直接抛出
	 * {@link io.github.butterfly.core.BusinessException}。
	 * @param joinPoint {@code AspectJ} 连接点,需为方法执行连接点,其签名可转换为 {@link MethodSignature}
	 * 以取得目标 方法及其参数
	 * @return 目标方法的返回值
	 * @throws Throwable 目标方法自身抛出的异常原样向上抛出;此外,未能获取到锁时抛出
	 * {@link io.github.butterfly.core.BusinessException},消息为 {@code 请稍后再试}
	 */
	@Around("cut()")
	public Object interceptor(ProceedingJoinPoint joinPoint) throws Throwable {
		Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
		Idempotent idempotent = method.getAnnotation(Idempotent.class);

		if (idempotent == null) {
			return joinPoint.proceed();
		}

		String key = buildKey(idempotent, method, joinPoint.getArgs());

		RLock lock = this.redissonClient.getLock(key);

		boolean locked = false;
		try {
			locked = lock.tryLock(0, -1, TimeUnit.SECONDS);

			if (!locked) {
				throw new BusinessException("请稍后再试");
			}

			return joinPoint.proceed();

		}
		finally {
			if (locked && lock.isHeldByCurrentThread()) {
				lock.unlock();
			}
		}
	}

	private String buildKey(Idempotent rateLimit, Method method, Object[] args) {
		String value = method.getDeclaringClass().getName() + "." + method.getName();
		String spElValue = this.spelSup.parseSpel(method, rateLimit.lockValue(), args);
		if (Validator.isNotEmpty(spElValue)) {
			value = value + "#" + spElValue;
		}
		return value;
	}

}
