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
import com.alibaba.otter.canal.client.impl.SimpleCanalConnector;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.net.InetSocketAddress;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 校验直连模式连接器的构造与默认值收敛:主机端口、ACL、超时覆盖,以及实例名的三级兜底。只造对象、不建连接,因此不触网。
 */
class CanalMessageSourceFactoryTests {

	private final CanalProperties properties = new CanalProperties();

	@Test
	void resolvesDestinationFromApplicationName() {
		assertThat(
				factory(new MockEnvironment().withProperty("spring.application.name", "order-service")).destination())
			.isEqualTo("order-service");
		assertThat(factory(new MockEnvironment()).destination()).isEqualTo("example");

		this.properties.setDestination("custom");
		assertThat(
				factory(new MockEnvironment().withProperty("spring.application.name", "order-service")).destination())
			.isEqualTo("custom");
	}

	@Test
	void createsSingleConnectorFromHostAndPort() {
		this.properties.setHost("10.1.1.1");
		this.properties.setPort(11112);
		this.properties.setUsername("canal");
		this.properties.setPassword("secret");

		CanalConnector connector = factory(new MockEnvironment()).createConnector();

		assertThat(connector).isInstanceOf(SimpleCanalConnector.class);
		SimpleCanalConnector simpleConnector = (SimpleCanalConnector) connector;
		assertThat(simpleConnector.getAddress()).isEqualTo(new InetSocketAddress("10.1.1.1", 11112));
		assertThat(simpleConnector.getUsername()).isEqualTo("canal");
		assertThat(simpleConnector.getPassword()).isEqualTo("secret");
	}

	@Test
	void appliesConfiguredTimeouts() {
		this.properties.setSoTimeout(Duration.ofSeconds(5));
		this.properties.setIdleTimeout(Duration.ofMinutes(2));

		SimpleCanalConnector connector = (SimpleCanalConnector) factory(new MockEnvironment()).createConnector();

		assertThat(connector.getSoTimeout()).isEqualTo(5000);
		assertThat(connector.getIdleTimeout()).isEqualTo(120000);
	}

	@Test
	void keepsCanalTimeoutsWhenTheyAreNotConfigured() {
		SimpleCanalConnector connector = (SimpleCanalConnector) factory(new MockEnvironment()).createConnector();

		assertThat(connector.getSoTimeout()).isEqualTo(60000);
		assertThat(connector.getIdleTimeout()).isEqualTo(3600000);
	}

	@Test
	void createsConnectorSourceWithoutConnecting() {
		CanalMessageSource source = factory(new MockEnvironment()).create();

		assertThat(source).isInstanceOf(CanalConnectorMessageSource.class);
		// 连接器要等消费端启动、调用 connect() 时才创建,应用启动阶段不碰网络
		assertThat(((CanalConnectorMessageSource) source).connector()).isNull();
	}

	private CanalMessageSourceFactory factory(MockEnvironment environment) {
		return new CanalMessageSourceFactory(this.properties, environment);
	}

}
