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

package io.github.butterfly.canal.autoconfigure;

import com.alibaba.otter.canal.client.CanalConnector;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * canal 自动配置:注册处理器基础设施、按模式创建消息来源,并按消费方式注册同步或异步消费端.
 * <p>
 * 生效条件与默认值:
 * <ul>
 * <li>类路径上有 canal-client 且 {@code butterfly.canal.enabled} 不为 {@code false} 时生效;</li>
 * <li>直连 {@code butterfly.canal.host}:{@code butterfly.canal.port} 上的 canal server;</li>
 * <li>默认 {@code butterfly.canal.consumer-type=sync},即同步消费;</li>
 * <li>默认 {@code butterfly.canal.auto-startup=true},即应用启动后自动开始消费;测试或延迟启动时把它设为
 * {@code false},再自行调用 {@link CanalEventConsumer#start()}。</li>
 * </ul>
 * <p>
 * 每个 Bean 都带 {@code @ConditionalOnMissingBean},使用方可以按需替换:
 * <ul>
 * <li>自定义 {@link CanalRowMapper} 接管行数据到实体的映射;</li>
 * <li>自定义 {@link CanalMessageSource} 接入 canal 的其它连接方式(集群、MQ 等);</li>
 * <li>自定义 {@link CanalEventConsumer} 完全接管消费方式,此时同步/异步两个 Bean 都会退让;</li>
 * <li>{@code consumer-type=async} 时定义名为 {@code butterflyCanalAsyncExecutor} 的
 * {@link AsyncTaskExecutor} Bean 替换默认线程池。</li>
 * </ul>
 * <p>
 * 本配置只创建对象、不建立连接:连接发生在消费端启动时,因此 canal server 暂时不可用不会阻塞应用启动,消费端会按
 * {@code butterfly.canal.error-back-off} 不断重连。
 */
@AutoConfiguration
@ConditionalOnClass(CanalConnector.class)
@ConditionalOnProperty(prefix = "butterfly.canal", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CanalProperties.class)
public class ButterflyCanalAutoConfiguration {

	/**
	 * 默认行数据映射器.
	 * @return 基于 Spring 属性绑定的映射器
	 */
	@Bean
	@ConditionalOnMissingBean
	CanalRowMapper butterflyCanalRowMapper() {
		return new BeanWrapperCanalRowMapper();
	}

	/**
	 * 事件分发器.
	 * @return 分发器
	 */
	@Bean
	@ConditionalOnMissingBean
	CanalEventDispatcher butterflyCanalEventDispatcher() {
		return new CanalEventDispatcher();
	}

	/**
	 * 处理器注册器:扫描容器里的 {@link CanalRowHandler} 与 {@link CanalListener} 并注册到分发器.
	 * @param beanFactory 用于扫描处理器 Bean
	 * @param dispatcher 事件分发器
	 * @param rowMapper 行数据映射器
	 * @return 注册器
	 */
	@Bean
	@ConditionalOnMissingBean
	CanalHandlerRegistrar butterflyCanalHandlerRegistrar(ListableBeanFactory beanFactory,
			CanalEventDispatcher dispatcher, CanalRowMapper rowMapper) {
		return new CanalHandlerRegistrar(beanFactory, dispatcher, rowMapper);
	}

	/**
	 * 创建直连 canal server 的消息来源,不在此时建立连接.
	 * @param properties canal 配置
	 * @param environment 用于解析 {@code spring.application.name} 这一跨模块默认值
	 * @return 消息来源
	 */
	@Bean
	@ConditionalOnMissingBean
	CanalMessageSource butterflyCanalMessageSource(CanalProperties properties, Environment environment) {
		return new CanalMessageSourceFactory(properties, environment).create();
	}

	/**
	 * 同步消费端,{@code butterfly.canal.consumer-type} 为 {@code sync}(默认)时注册.
	 * @param messageSource 消息来源
	 * @param dispatcher 事件分发器
	 * @param properties canal 配置
	 * @return 同步消费端
	 */
	@Bean
	@ConditionalOnMissingBean(CanalEventConsumer.class)
	@ConditionalOnProperty(prefix = "butterfly.canal", name = "consumer-type", havingValue = "sync",
			matchIfMissing = true)
	SyncCanalEventConsumer butterflySyncCanalEventConsumer(CanalMessageSource messageSource,
			CanalEventDispatcher dispatcher, CanalProperties properties) {
		return new SyncCanalEventConsumer(messageSource, dispatcher, properties);
	}

	/**
	 * 异步消费端的线程池,{@code butterfly.canal.consumer-type} 为 {@code async} 时注册.
	 * <p>
	 * Bean 名称固定为 {@code butterflyCanalAsyncExecutor},既便于使用方按名替换,也避免与 Boot 的
	 * {@code applicationTaskExecutor} 冲突;线程名与参数取自 {@link CanalProperties.Async}。
	 * @param properties canal 配置
	 * @return 线程池
	 */
	@Bean(name = "butterflyCanalAsyncExecutor")
	@ConditionalOnMissingBean(name = "butterflyCanalAsyncExecutor")
	@ConditionalOnProperty(prefix = "butterfly.canal", name = "consumer-type", havingValue = "async")
	ThreadPoolTaskExecutor butterflyCanalAsyncExecutor(CanalProperties properties) {
		CanalProperties.Async async = properties.getAsync();
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(async.getCorePoolSize());
		executor.setMaxPoolSize(async.getMaxPoolSize());
		executor.setQueueCapacity(async.getQueueCapacity());
		executor.setThreadNamePrefix(async.getThreadNamePrefix());
		// 关闭时等待已提交的事件处理完,避免线程池被提前销毁
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(30);
		return executor;
	}

	/**
	 * 异步消费端,{@code butterfly.canal.consumer-type} 为 {@code async} 时注册.
	 * @param messageSource 消息来源
	 * @param dispatcher 事件分发器
	 * @param properties canal 配置
	 * @param executor 处理事件的线程池
	 * @return 异步消费端
	 */
	@Bean
	@ConditionalOnMissingBean(CanalEventConsumer.class)
	@ConditionalOnProperty(prefix = "butterfly.canal", name = "consumer-type", havingValue = "async")
	AsyncCanalEventConsumer butterflyAsyncCanalEventConsumer(CanalMessageSource messageSource,
			CanalEventDispatcher dispatcher, CanalProperties properties,
			@Qualifier("butterflyCanalAsyncExecutor") AsyncTaskExecutor executor) {
		return new AsyncCanalEventConsumer(messageSource, dispatcher, properties, executor);
	}

}
