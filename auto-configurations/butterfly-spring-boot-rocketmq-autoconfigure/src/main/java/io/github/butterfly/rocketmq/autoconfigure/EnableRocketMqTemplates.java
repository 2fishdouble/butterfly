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

package io.github.butterfly.rocketmq.autoconfigure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 为若干实体类批量声明 RocketMQ 主题、消费组与死信主题,并按实体类提供绑定好主题的发送器.
 * <p>
 * 标注在配置类上,通过 {@code @Import} 引入 {@link RocketMqRegistrar},由它按 {@link #value()} 中的每个实体类
 * 声明一组 Bean:
 * <ul>
 * <li>{@code <实体>RocketMqTopic}:该实体类解析后的
 * {@link RocketMqProperties.TopicDefinition}(主主题名、消费组名、 死信主题名 {@code %DLQ%<消费组>}、重试主题名
 * {@code %RETRY%<消费组>}、标签、队列数与建主题开关);</li>
 * <li>{@code <实体>RocketMqSender}:绑定到该实体类主主题的 {@link RocketMqSender},底层复用 starter 自动配置的
 * 那一个 {@code RocketMQClientTemplate}。</li>
 * </ul>
 * <p>
 * 主主题的实际创建由 {@link RocketMqTopicInitializer} 在启动时通过 {@link RocketMqTopicAdmin} 完成。死信主题与
 * 重试主题是 RocketMQ 的系统主题,由 Broker 自动创建,本模块只推导它们的名字。
 * <p>
 * 例如 {@code @EnableRocketMqTemplates({ User.class, Order.class })} 会为两个实体类各注册一组 Bean,得到
 * {@code user} / {@code order} 两个主题,以及 {@code %DLQ%user} / {@code %DLQ%order} 两个死信主题。
 * <p>
 * 若容器中已存在同名 Bean 定义,则跳过该实体类的对应注册。
 * <p>
 * 主题名、消费组名、死信与重试主题名、标签、队列数等全部参数均可在 {@code butterfly.rocketmq.*} 下可选配置,见
 * {@link RocketMqProperties}。该注解已通过 {@code @EnableConfigurationProperties} 注册该配置类,因此无需额外
 * 标注。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@EnableConfigurationProperties(RocketMqProperties.class)
@Import(RocketMqRegistrar.class)
public @interface EnableRocketMqTemplates {

	/**
	 * 需要声明主题、消费组与发送器的实体类数组.
	 * <p>
	 * 数组元素既决定主题与消费组的默认名称(实体类简单名首字母小写),也决定各 Bean 的名称前缀。
	 * <p>
	 * 默认值为空数组,即不声明任何 Bean。
	 * @return 实体类数组,默认空数组
	 */
	Class<?>[] value() default {};

}
