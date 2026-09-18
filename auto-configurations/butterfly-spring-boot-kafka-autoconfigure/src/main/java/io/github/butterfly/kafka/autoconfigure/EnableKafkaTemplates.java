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

package io.github.butterfly.kafka.autoconfigure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 为若干实体类批量启用各自专用的 {@code KafkaTemplate} Bean,并自动创建对应主题.
 * <p>
 * 标注在配置类上,通过 {@code @Import} 引入 {@link KafkaTemplateRegistrar},由它按 {@link #value()} 中的
 * 每个实体类注册一个 {@code KafkaTemplate<String, T>} Bean:Bean 名称为实体类简单名首字母小写后拼接
 * {@code "KafkaTemplate"},key 使用 String 序列化器,value 使用以该实体类为目标类型的 JSON 序列化器。
 * <p>
 * 例如 {@code @EnableKafkaTemplates({ User.class, Order.class })} 将注册
 * {@code userKafkaTemplate} 与 {@code orderKafkaTemplate} 两个 Bean;若容器中已存在同名 Bean
 * 定义,则跳过该实体类的注册。
 * <p>
 * 同时为每个实体类注册主题与重试相关的 Bean:
 * <ul>
 * <li>主主题的 {@code NewTopic}(名称为实体类简单名首字母小写后拼接 {@code "NewTopic"}),由 {@code KafkaAdmin}
 * 在启动时创建;主题名默认取实体类简单名首字母小写,分区数与副本数默认 {@code 1};</li>
 * <li>该主题专属的 {@code RetryTopicConfiguration},让落在该主题上的 {@code @KafkaListener} <b>无需</b>标注
 * {@code @RetryableTopic} 即默认获得非阻塞重试与死信投递能力;</li>
 * <li>重试主题({@code <主题>-retry-<延迟>})与死信主题({@code <主题>-dlt})的 {@code NewTopics},随主主题
 * 一并创建。</li>
 * </ul>
 * 以上四项均可在 {@code butterfly.kafka.topic.*} 下可选配置,见 {@link KafkaTopicProperties}。该注解已通过
 * {@code @EnableConfigurationProperties} 注册该配置类,因此无需额外标注。
 * <p>
 * Broker 地址、安全认证、acks 等生产者参数仍由 Spring Boot 的 {@code spring.kafka.*} 负责:注册器会复用 容器中已有的
 * {@code ProducerFactory} 配置,仅替换 key/value 序列化器。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@EnableConfigurationProperties(KafkaTopicProperties.class)
@Import(KafkaTemplateRegistrar.class)
public @interface EnableKafkaTemplates {

	/**
	 * 需要注册专用 {@code KafkaTemplate} 的实体类数组.
	 * <p>
	 * 数组元素决定所注册模板的 value 序列化目标类型;默认值为空数组,即不注册任何 Bean。
	 * @return 实体类数组,默认空数组
	 */
	Class<?>[] value() default {};

}
