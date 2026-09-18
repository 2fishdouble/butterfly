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

package io.github.butterfly.rabbitmq.autoconfigure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 为若干实体类批量声明 RabbitMQ 拓扑,并按实体类提供带重试与死信的监听容器工厂.
 * <p>
 * 标注在配置类上,通过 {@code @Import} 引入 {@link RabbitMqRegistrar},由它按 {@link #value()} 中的每个实体类
 * 声明一组 Bean:
 * <ul>
 * <li>{@code <实体>Declarables}:主交换机、主队列与绑定;默认追加死信交换机、死信队列与绑定,主队列通过 {@code x-dead-letter-*}
 * 把失败消息投给它;可选追加延时交换机、延时队列与绑定。这些 Bean 由 Boot 注册的 {@code RabbitAdmin} 在启动时声明到 Broker;</li>
 * <li>{@code <实体>RabbitListenerContainerFactory}:预置按配置构建的重试通知链;重试耗尽后拒绝原投递,由 Broker 按主
 * 队列上的 {@code x-dead-letter-*} 把它 dead-letter 到该实体类的死信交换机。</li>
 * </ul>
 * <p>
 * 例如 {@code @EnableRabbitMqTemplates({ User.class, Order.class })} 会为两个实体类各注册一组 Bean。
 * <p>
 * 若容器中已存在同名 Bean 定义,则跳过该实体类的对应注册。
 * <p>
 * 收发消息统一使用容器中 Boot 自动配置的那一个 {@code RabbitTemplate}。
 * <p>
 * 本模块<b>不</b>按实体类注册多个模板,只提供一个共享的 JSON 消息转换器,让模板与各监听容器工厂都按 JSON 收发。
 * <p>
 * 发送时显式传入交换机与路由键,两者由 {@link RabbitMqProperties#resolve(Class)} 给出。
 * <p>
 * 拓扑名称、交换机类型、持久化、重试、死信与延时等全部参数均可在 {@code butterfly.rabbitmq.*} 下可选配置,见
 * {@link RabbitMqProperties}。该注解已通过 {@code @EnableConfigurationProperties} 注册该配置类,因此无需额外
 * 标注。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@EnableConfigurationProperties(RabbitMqProperties.class)
@Import(RabbitMqRegistrar.class)
public @interface EnableRabbitMqTemplates {

	/**
	 * 需要声明拓扑与监听容器工厂的实体类数组.
	 * <p>
	 * 数组元素既决定拓扑默认名称(实体类简单名首字母小写),也决定各 Bean 的名称前缀。
	 * <p>
	 * 默认值为空数组,即不声明任何 Bean。
	 * @return 实体类数组,默认空数组
	 */
	Class<?>[] value() default {};

}
