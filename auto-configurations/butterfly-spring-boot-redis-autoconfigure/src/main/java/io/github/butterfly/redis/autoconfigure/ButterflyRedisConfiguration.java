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

import io.github.butterfly.autoconfigure.SpelSup;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Redis 相关自动配置.
 * <p>
 * 仅在类路径上存在 {@link RedissonClient} 时生效,负责注册 {@link IdempotentAspect} 幂等切面;其余 Redis
 * 基础设施(如连接工厂、通用 {@code RedisTemplate})仍由 Spring Boot / Spring Data Redis 的官方自动配置提供。
 *
 * @see org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration
 */
@AutoConfiguration
@ConditionalOnClass(RedissonClient.class)
public class ButterflyRedisConfiguration {

	/**
	 * {@code @Idempotent} 幂等切面.
	 * <p>
	 * 此前它被直接写进 {@code AutoConfiguration.imports}(但它只是个 {@code @Component}); 现改为标准
	 * {@code @Bean} 注册,并把 AspectJ / hutool 的存在性条件下到方法上, 缺少任一依赖时优雅跳过,而不是抛
	 * NoClassDefFoundError。
	 * @param spelSup 解析 {@link Idempotent#lockValue()} 中 SpEL 表达式所需的支持组件
	 * @param redissonClient 用于获取分布式锁的 Redisson 客户端
	 * @return 由给定参数构造的 {@link IdempotentAspect} 实例
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnClass(name = { "org.aspectj.lang.ProceedingJoinPoint", "cn.hutool.core.lang.Validator" })
	public IdempotentAspect idempotentAspect(SpelSup spelSup, RedissonClient redissonClient) {
		return new IdempotentAspect(spelSup, redissonClient);
	}

}
