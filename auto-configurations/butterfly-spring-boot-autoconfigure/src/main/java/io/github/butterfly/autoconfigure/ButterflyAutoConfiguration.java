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

package io.github.butterfly.autoconfigure;

import io.github.butterfly.core.BaseEnum;
import io.github.butterfly.core.PatternConstant;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ext.javatime.deser.LocalDateDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalTimeDeserializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalTimeSerializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Butterfly 基础自动配置,注册框架通用的 SpEL 解析辅助与 Jackson 定制.
 * <p>
 * 本类注册两类 Bean:
 * <ul>
 * <li>{@link SpelSup}:SpEL 表达式解析辅助,仅在 classpath 上存在 hutool 的
 * {@code cn.hutool.core.lang.Validator} 时注册(hutool 为可选依赖);</li>
 * <li>{@link JsonMapperBuilderCustomizer}:定制 Spring Boot 自动配置的 Jackson 构建器,追加
 * {@link BaseEnum} 枚举序列化/反序列化、{@code Long}/{@code long} 转字符串(避免前端 JavaScript 精度丢失)以及基于
 * {@link PatternConstant} 中格式的 {@code LocalDateTime}/{@code LocalDate}/ {@code LocalTime}
 * 读写模块。</li>
 * </ul>
 * Web 相关的 {@link TraceIdFilter} 放在嵌套的 {@link WebConfiguration} 里,原因见该类说明。
 */
@AutoConfiguration
@EnableConfigurationProperties(WebProperties.class)
public class ButterflyAutoConfiguration {

	/**
	 * 注册 {@link SpelSup} Bean,供 Redis 幂等锁等场景解析注解中的 SpEL 表达式.
	 * <p>
	 * 生效条件:classpath 上存在 {@code cn.hutool.core.lang.Validator}(hutool 是可选依赖, 缺失时本 Bean
	 * 及依赖它的自动配置会被跳过);且容器中尚无同类型 Bean 时才注册。
	 * @param beanFactory 用于解析 SpEL 表达式中的 {@code @beanName} 引用
	 * @return 绑定该 {@link BeanFactory} 的 {@link SpelSup} 实例
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnClass(name = "cn.hutool.core.lang.Validator")
	public SpelSup spelSup(BeanFactory beanFactory) {
		return new SpelSup(beanFactory);
	}

	/**
	 * 注册 {@link JsonMapperBuilderCustomizer} Bean,在 Spring Boot 构建 Jackson 的
	 * {@code JsonMapper} 时追加三个模块.
	 * <p>
	 * 追加内容:
	 * <ul>
	 * <li>BaseEnum 模块:为 {@link BaseEnum} 注册 {@link BaseEnumDeserializer} 和
	 * {@link BaseEnumSerializer},使实现该接口的枚举按 code 收发;</li>
	 * <li>Long 模块:为 {@code Long}、{@code Long.TYPE} 与 {@code long} 注册
	 * {@link ToStringSerializer},把长整型序列化为 JSON 字符串,避免前端 JavaScript 数值精度丢失
	 * (只影响序列化,反序列化仍接受数字与字符串);</li>
	 * <li>时间模块:按
	 * {@link PatternConstant#DATE_TIME_FORMAT}、{@link PatternConstant#DATE_FORMAT}、
	 * {@link PatternConstant#TIME_FORMAT} 分别为 {@code LocalDateTime}、{@code LocalDate}、
	 * {@code LocalTime} 注册序列化器与反序列化器。</li>
	 * </ul>
	 * <p>
	 * 仅在容器中不存在同类型定制器 Bean 时注册;返回的定制逻辑在构建 {@code JsonMapper} 时执行。
	 * @return 向 Jackson 构建器追加上述模块的定制器
	 */
	@Bean
	@ConditionalOnMissingBean
	public JsonMapperBuilderCustomizer jsonMapperBuilderCustomizer() {
		return (builder) -> {
			SimpleModule baseEnumModule = new SimpleModule();
			baseEnumModule.addDeserializer(BaseEnum.class, new BaseEnumDeserializer());
			baseEnumModule.addSerializer(BaseEnum.class, new BaseEnumSerializer());
			builder.addModule(baseEnumModule);

			SimpleModule longModule = new SimpleModule();
			longModule.addSerializer(Long.class, ToStringSerializer.instance);
			longModule.addSerializer(Long.TYPE, ToStringSerializer.instance);
			longModule.addSerializer(long.class, ToStringSerializer.instance);
			builder.addModule(longModule);

			SimpleModule timeModule = new SimpleModule();
			timeModule.addDeserializer(LocalDateTime.class,
					new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(PatternConstant.DATE_TIME_FORMAT)));
			timeModule.addDeserializer(LocalDate.class,
					new LocalDateDeserializer(DateTimeFormatter.ofPattern(PatternConstant.DATE_FORMAT)));
			timeModule.addDeserializer(LocalTime.class,
					new LocalTimeDeserializer(DateTimeFormatter.ofPattern(PatternConstant.TIME_FORMAT)));
			timeModule.addSerializer(LocalDateTime.class,
					new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(PatternConstant.DATE_TIME_FORMAT)));
			timeModule.addSerializer(LocalDate.class,
					new LocalDateSerializer(DateTimeFormatter.ofPattern(PatternConstant.DATE_FORMAT)));
			timeModule.addSerializer(LocalTime.class,
					new LocalTimeSerializer(DateTimeFormatter.ofPattern(PatternConstant.TIME_FORMAT)));
			builder.addModule(timeModule);
		};
	}

	/**
	 * Web 相关 Bean:classpath 上有 spring-web(Servlet 过滤器基类可用)时才注册.
	 * <p>
	 * 之所以把 {@code traceIdFilter} 放进嵌套配置类,而不是直接写在 {@link ButterflyAutoConfiguration}
	 * 上:Spring 在评估 {@link ConditionalOnMissingBean} 时会反射读取 配置类全部方法的签名,只要有一个方法返回 Web
	 * 类型({@link TraceIdFilter} 继承 Servlet 的 {@code OncePerRequestFilter}),classpath 上没有
	 * spring-web 的应用(例如只跑 MQ 的沙箱)就会在启动阶段抛
	 * {@code NoClassDefFoundError}。嵌套配置类的条件只读注解元数据(ASM),不需要加载 Web 类型,因此缺少 spring-web
	 * 时这个类整体被跳过。
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(name = "org.springframework.web.filter.OncePerRequestFilter")
	static class WebConfiguration {

		/**
		 * 装配 TraceId 过滤器.
		 * @param properties butterfly-web 配置
		 * @return traceId 过滤器
		 */
		@Bean
		@ConditionalOnMissingBean
		@ConditionalOnProperty(prefix = "butterfly.web.trace-id", name = "enabled", havingValue = "true",
				matchIfMissing = true)
		TraceIdFilter traceIdFilter(WebProperties properties) {
			return new TraceIdFilter(properties.getTraceId().getHeader());
		}

	}

}
