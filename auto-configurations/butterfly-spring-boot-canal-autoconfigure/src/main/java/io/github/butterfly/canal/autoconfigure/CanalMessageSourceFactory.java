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
import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.client.impl.SimpleCanalConnector;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.net.InetSocketAddress;
import java.time.Duration;

/**
 * 消息来源工厂:创建直连 canal server 的 {@link CanalMessageSource}.
 * <p>
 * 这里统一做默认值收敛:{@link CanalProperties#destination} 为空时取
 * {@code spring.application.name},再兜底为 {@code example}(canal 的默认实例名)。
 * <p>
 * 本类只负责"造对象",不建立网络连接:连接器由 {@link CanalConnectorMessageSource#connect()} 在消费端启动时才创建,
 * 因此应用启动阶段不会因为 canal server 暂时不可用而失败。
 * <p>
 * 需要接入 canal 的其它连接方式(集群、MQ 等)时,直接实现 {@link CanalMessageSource} 并注册为 Bean,本工厂随之退让。
 */
public class CanalMessageSourceFactory {

	private static final String APPLICATION_NAME_PROPERTY = "spring.application.name";

	private static final String FALLBACK_DESTINATION = "example";

	private final CanalProperties properties;

	private final Environment environment;

	/**
	 * 创建工厂.
	 * @param properties canal 配置
	 * @param environment 用于解析 {@code spring.application.name} 这一跨模块的默认值
	 */
	public CanalMessageSourceFactory(CanalProperties properties, Environment environment) {
		this.properties = properties;
		this.environment = environment;
	}

	/**
	 * 创建消息来源,不建立连接.
	 * @return 直连 canal server 的消息来源
	 */
	public CanalMessageSource create() {
		return new CanalConnectorMessageSource(this::createConnector, destination(), this.properties.getBatchSize(),
				this.properties.getFilter());
	}

	/**
	 * 创建 canal 连接器,不建立连接.
	 * <p>
	 * 由 {@link CanalConnectorMessageSource#connect()} 在消费端启动时调用,因此 netty 客户端的建立也发生在应用
	 * 启动之后。包内可见,便于测试直接断言连接器类型与超时参数,而无需真的连上 canal server。
	 * @return canal 连接器
	 */
	CanalConnector createConnector() {
		CanalConnector connector = CanalConnectors.newSingleConnector(
				new InetSocketAddress(this.properties.getHost(), this.properties.getPort()), destination(),
				this.properties.getUsername(), this.properties.getPassword());

		return applyTimeouts(connector);
	}

	/**
	 * 解析生效的 canal 实例名.
	 * @return {@link CanalProperties#getDestination()} 非空时的取值,否则为
	 * {@code spring.application.name},再兜底为 {@code example}
	 */
	public String destination() {
		String destination = this.properties.getDestination();
		if (StringUtils.hasText(destination)) {
			return destination;
		}

		return this.environment.getProperty(APPLICATION_NAME_PROPERTY, FALLBACK_DESTINATION);
	}

	/**
	 * 把 canal 自带工厂设置的默认超时(读 60 秒、空闲 1 小时)按配置覆盖.
	 * <p>
	 * 只在配置了对应项时才覆盖,没配置时保留 canal 的默认值。
	 * @param connector canal 连接器
	 * @return 传入的连接器,便于链式调用
	 */
	private CanalConnector applyTimeouts(CanalConnector connector) {
		Duration soTimeout = this.properties.getSoTimeout();
		Duration idleTimeout = this.properties.getIdleTimeout();

		if (connector instanceof SimpleCanalConnector simpleConnector) {
			if (soTimeout != null) {
				simpleConnector.setSoTimeout(toIntMillis(soTimeout));
			}
			if (idleTimeout != null) {
				simpleConnector.setIdleTimeout(toIntMillis(idleTimeout));
			}
		}

		return connector;
	}

	private static int toIntMillis(Duration duration) {
		return Math.toIntExact(duration.toMillis());
	}

}
