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

import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 为若干实体类批量启用各自专用的 {@code RedisTemplate} Bean.
 * <p>
 * 标注在配置类上,通过 {@code @Import} 引入 {@link RedisTemplateRegistrar},由它按 {@link #value()} 中的
 * 每个实体类注册一个 {@code RedisTemplate<String, T>} Bean:Bean 名称为实体类简单名首字母小写后拼接
 * {@code "RedisTemplate"},key/hashKey 使用 String 序列化器,value/hashValue 使用以该实体类为目标的 Jackson
 * JSON 序列化器。
 * <p>
 * 例如 {@code @EnableRedisTemplates({ User.class, Order.class })} 将注册
 * {@code userRedisTemplate} 与 {@code orderRedisTemplate} 两个 Bean;若容器中已存在同名 Bean
 * 定义,则跳过该实体类的注册。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(RedisTemplateRegistrar.class)
public @interface EnableRedisTemplates {

	/**
	 * 需要注册专用 {@code RedisTemplate} 的实体类数组.
	 * <p>
	 * 数组元素决定所注册模板的 value 序列化目标类型;默认值为空数组,即不注册任何 Bean。
	 * @return 实体类数组,默认空数组
	 */
	Class<?>[] value() default {};

}
