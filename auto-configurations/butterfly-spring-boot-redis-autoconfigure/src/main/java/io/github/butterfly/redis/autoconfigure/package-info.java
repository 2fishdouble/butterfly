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

/**
 * Redis 自动配置包.
 * <p>
 * 主要提供两类能力:
 * <ul>
 * <li>通过 {@link io.github.butterfly.redis.autoconfigure.EnableRedisTemplates}
 * 为指定实体类注册各自的专用 {@code RedisTemplate} Bean(见
 * {@link io.github.butterfly.redis.autoconfigure.RedisTemplateRegistrar});</li>
 * <li>通过 {@link io.github.butterfly.redis.autoconfigure.Idempotent} 与
 * {@link io.github.butterfly.redis.autoconfigure.IdempotentAspect} 提供基于 Redisson
 * 分布式锁的方法级幂等控制。</li>
 * </ul>
 * <p>
 * 自动配置类 {@link io.github.butterfly.redis.autoconfigure.ButterflyRedisConfiguration} 仅在
 * {@code RedissonClient} 存在时生效。
 */
@NullMarked
package io.github.butterfly.redis.autoconfigure;

import org.jspecify.annotations.NullMarked;
