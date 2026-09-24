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

import lombok.Data;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * canal 配置,前缀 {@code butterfly.canal}.
 * <p>
 * 本模块直连单个 canal server({@code CanalConnectors#newSingleConnector}),常用配置项:
 * <ul>
 * <li>{@link #host} 与 {@link #port}:canal server 地址,默认 {@code 127.0.0.1:11111};</li>
 * <li>{@link #consumerType}:同步或异步处理事件,默认同步;</li>
 * <li>{@link #destination}:canal 实例名,未配置时取 {@code spring.application.name},再兜底为
 * {@code example};</li>
 * <li>{@link #filter}:canal 订阅表达式,默认订阅所有库的所有表;</li>
 * <li>{@link #autoStartup}:是否随应用启动消费,设为 {@code false} 时只注册 Bean、不拉取数据,便于测试或延迟启动。</li>
 * </ul>
 * <p>
 * 拉取与失败重试相关参数见 {@link #batchSize}、{@link #timeout}、{@link #errorBackOff}。集群模式与 MQ 模式不在本模块
 * 范围内,需要时实现 {@link CanalMessageSource} 替换默认实现。
 */
@Data
@ConfigurationProperties(prefix = "butterfly.canal")
public class CanalProperties {

	/**
	 * 是否启用 canal 自动配置,默认 {@code true}.
	 */
	private boolean enabled = true;

	/**
	 * 事件处理方式,默认 {@link CanalConsumerType#SYNC}.
	 */
	private CanalConsumerType consumerType = CanalConsumerType.SYNC;

	/**
	 * 是否随应用启动消费,默认 {@code true}.
	 * <p>
	 * 设为 {@code false} 时消费端 Bean 依然注册在容器里,只是不自动开始拉取,可由使用方自行调用
	 * {@link CanalEventConsumer#start()}。
	 */
	private boolean autoStartup = true;

	/**
	 * canal 实例名(destination);为空时取 {@code spring.application.name},再兜底为 {@code example}.
	 */
	private @Nullable String destination;

	/**
	 * canal 订阅表达式,格式为 {@code 库名.表名},支持 {@code .*} 与逗号分隔的多项,默认订阅所有库的所有表.
	 */
	private @Nullable String filter = ".*\\..*";

	/**
	 * canal server 的 ACL 用户名,没有开启 ACL 时留空.
	 */
	private String username = "";

	/**
	 * canal server 的 ACL 密码,没有开启 ACL 时留空.
	 */
	private String password = "";

	/**
	 * canal server 的主机名.
	 */
	private String host = "127.0.0.1";

	/**
	 * canal server 的端口,canal server 默认 {@code 11111}.
	 */
	private int port = 11111;

	/**
	 * 单次拉取的最大事件条数,即 {@code getWithoutAck} 的 batchSize,默认 1000.
	 */
	private int batchSize = 1000;

	/**
	 * 单次拉取的等待时间,默认 1 秒.
	 * <p>
	 * 作为 {@code getWithoutAck} 的超时;设为 {@code 0} 表示一直阻塞到攒够 {@link #batchSize} 条,此时停止应用依赖
	 * {@code stopRunning} 打断阻塞。
	 */
	private Duration timeout = Duration.ofSeconds(1);

	/**
	 * 底层连接的网络读超时;为空时沿用 canal 默认的 60 秒.
	 */
	private @Nullable Duration soTimeout;

	/**
	 * 底层连接的空闲超时;为空时沿用 canal 默认的 1 小时.
	 */
	private @Nullable Duration idleTimeout;

	/**
	 * 拉取或处理失败后、下一次拉取前的等待时间,默认 1 秒.
	 * <p>
	 * 处理失败会整批回滚,如果处理器对同样数据必然失败,没有等待就会形成紧循环刷日志,因此保留这个退避时间; 设为 {@code 0} 表示不等待。
	 */
	private Duration errorBackOff = Duration.ofSeconds(1);

	/**
	 * 异步消费的线程池配置.
	 */
	private Async async = new Async();

	/**
	 * 异步消费的线程池配置,前缀 {@code butterfly.canal.async}.
	 * <p>
	 * 线程池由 Spring 的 {@code ThreadPoolTaskExecutor} 实现,因此实际并发数遵循它的规则:队列没满时只用
	 * {@link #corePoolSize} 个线程,队列填满后才扩到 {@link #maxPoolSize}。
	 */
	@Data
	public static class Async {

		/**
		 * 核心线程数,默认 2.
		 */
		private int corePoolSize = 2;

		/**
		 * 最大线程数,默认 8.
		 */
		private int maxPoolSize = 8;

		/**
		 * 队列容量,默认 64.
		 */
		private int queueCapacity = 64;

		/**
		 * 线程名前缀,默认 {@code butterfly-canal-}.
		 */
		private String threadNamePrefix = "butterfly-canal-";

	}

}
