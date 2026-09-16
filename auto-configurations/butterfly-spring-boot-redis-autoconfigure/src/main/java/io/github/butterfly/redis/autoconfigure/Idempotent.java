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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法级幂等控制注解.
 * <p>
 * 标注在需要幂等保护的方法上,由 {@link IdempotentAspect} 环绕拦截:以「方法所属类全名 + '.' + 方法名」为锁 key 前缀,拼接
 * {@link #lockValue()} 解析出的 SpEL 结果后,用 Redisson 的 {@code RLock} 以
 * {@code tryLock(0, -1, TimeUnit.SECONDS)}(不等待、不自动过期)立即尝试加锁;抢锁失败抛出
 * {@link io.github.butterfly.core.BusinessException}(消息为 {@code 请稍后再试}),方法正常结束或抛异常时,
 * 仅由持锁线程释放锁。
 * <p>
 * 未标注本注解的方法不会被拦截,直接放行。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

	/**
	 * 参与拼接锁 key 的 SpEL 表达式数组,通常用于按方法参数区分不同调用.
	 * <p>
	 * 每个元素依次求值,非空结果按数组顺序以 {@code "."} 连接;若最终结果非空,则以 {@code "#"} 拼接到 「类全名.方法名」之后,作为最终锁
	 * key。默认值为单个空字符串,等价于不按参数区分,同一方法的所有调用 共用同一把锁。
	 * @return {@code SpEL} 表达式数组,默认 {@code ""}
	 */
	String[] lockValue() default "";

}
