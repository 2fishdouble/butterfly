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

import org.redisson.spring.starter.RedissonAutoConfigurationV4;
import org.springframework.boot.autoconfigure.AutoConfiguration;

/**
 * Redisson 自动配置的说明性配置类(占位).
 * <p>
 * 本类不声明任何 Bean,仅作为 {@code @AutoConfiguration} 登记在册,并通过 {@code @see} 指向 Redisson 官方
 * starter 的自动配置 {@link RedissonAutoConfigurationV4};{@code RedissonClient} 等基础设施 Bean 实际由
 * 该官方自动配置在类路径存在相应依赖时提供。
 *
 * @see RedissonAutoConfigurationV4
 */
@AutoConfiguration
public class ButterflyRedissonConfiguration {

}
