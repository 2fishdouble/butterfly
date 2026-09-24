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

package io.github.butterfly.sandbox;

import io.github.butterfly.sandbox.curator.CuratorProperties;
import io.github.butterfly.sandbox.curator.CuratorZookeeperService;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.imps.CuratorFrameworkState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * 沙箱自检:配置来自 {@code application.yml}、路径拼装正确,且 {@code auto-startup=false} 时不会去连 ZooKeeper.
 * <p>
 * 这里刻意关掉建连:本用例只验证装配与路径规则,不需要有 ZK 在跑,因此在任何机器上都能执行。真正连上 ZK 的端到端验证见
 * {@link CuratorIntegrationTests}。
 */
@SpringBootTest(properties = { "butterfly.curator.auto-startup=false" })
class CuratorSandboxTests {

	/**
	 * 必须与 {@code application.yml} 中 {@code butterfly.curator.connect-string} 一致.
	 */
	private static final String CONNECT_STRING = "192.168.12.29:2181";

	/**
	 * 必须与 {@code application.yml} 中 {@code butterfly.curator.namespace} 一致.
	 */
	private static final String NAMESPACE = "sandbox-curator";

	/**
	 * 必须与 {@code application.yml} 中 {@code butterfly.curator.base-path} 一致.
	 */
	private static final String BASE_PATH = "/butterfly/sandbox/curator";

	@Autowired
	private CuratorProperties properties;

	@Autowired
	private CuratorZookeeperService service;

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void bindsCuratorConfigurationFromApplicationYaml() {
		assertThat(this.properties.getConnectString()).isEqualTo(CONNECT_STRING);
		assertThat(this.properties.getNamespace()).isEqualTo(NAMESPACE);
		assertThat(this.properties.getBasePath()).isEqualTo(BASE_PATH);
		// yml 里写的是 60s / 15s,能读成 Duration 说明时间单位配置按 Spring Boot 的宽松绑定生效
		assertThat(this.properties.getSessionTimeout()).isEqualTo(Duration.ofSeconds(60));
		assertThat(this.properties.getConnectionTimeout()).isEqualTo(Duration.ofSeconds(15));
	}

	/**
	 * {@code auto-startup=false} 时不注册客户端 bean;组件用配置自建一个不启动的客户端,并保持未连接状态。
	 */
	@Test
	void doesNotConnectAtStartup() {
		assertThat(this.applicationContext.getBeanNamesForType(CuratorFramework.class)).isEmpty();
		assertThat(this.service.connectString()).isEqualTo(CONNECT_STRING);
		assertThat(this.service.namespace()).isEqualTo(NAMESPACE);
		assertThat(this.service.connected()).isFalse();
		assertThat(this.service.state()).isEqualTo(CuratorFrameworkState.LATENT);
	}

	/**
	 * 节点名是相对根路径的一段,根路径由 {@code base-path} 决定;带上 namespace 后,写进 ZK 的真实路径是
	 * {@code /sandbox-curator/butterfly/sandbox/curator/computer-1}.
	 */
	@Test
	void resolvesNodePathsUnderBasePath() {
		assertThat(this.service.absolutePath(null)).isEqualTo(BASE_PATH);
		assertThat(this.service.absolutePath("")).isEqualTo(BASE_PATH);
		assertThat(this.service.absolutePath("computer-1")).isEqualTo(BASE_PATH + "/computer-1");
	}

	/**
	 * 节点名里出现 {@code /} 时直接拒绝,避免演示接口被用来写根路径之外的节点。
	 */
	@Test
	void rejectsNodeNamesContainingSlash() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.service.absolutePath("a/b"))
			.withMessageContaining("must not contain '/'");
	}

}
