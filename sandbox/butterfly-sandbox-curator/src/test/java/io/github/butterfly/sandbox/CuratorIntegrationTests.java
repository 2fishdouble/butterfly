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
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.imps.CuratorFrameworkState;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 端到端验证:真的连上 {@code application.yml} 里的 ZooKeeper,并跑通「创建 → 覆盖 → 读取 → 列子节点 → 删除」。
 * <p>
 * 与 canal、kafka 等沙箱一致,这里<b>需要</b>外部中间件:默认地址是 ZK 的 {@code 192.168.12.29:2181};换环境时用
 * {@code mvn -pl sandbox/butterfly-sandbox-curator test -Dbutterfly.curator.connect-string=host:2181}
 * 覆盖, 没有 ZK 时本类会失败,只验证装配请运行 {@link CuratorSandboxTests}。用例中的节点名带随机后缀,不会互相干扰,结束后自行清理。
 */
@SpringBootTest(properties = { "butterfly.curator.connection-timeout=5s", "butterfly.curator.session-timeout=10s" })
class CuratorIntegrationTests {

	@Autowired
	private CuratorFramework client;

	@Autowired
	private CuratorProperties properties;

	@Autowired
	private CuratorZookeeperService service;

	private final String nodeName = "computer-" + Long.toHexString(System.nanoTime());

	/**
	 * 用例自己的节点用完即删,不留垃圾;根路径保留,便于用 {@code zkCli} 观察。
	 * @throws Exception 连接不可用
	 */
	@AfterEach
	void cleanUp() throws Exception {
		this.service.delete(this.nodeName);
	}

	/**
	 * 客户端由 {@code CuratorClientConfiguration} 建出并已连上 ZK,启动阶段也已幂等建出根路径。
	 */
	@Test
	void connectsAndCreatesBasePathAtStartup() throws Exception {
		assertThat(this.client.getState()).isEqualTo(CuratorFrameworkState.STARTED);
		assertThat(this.service.connected()).isTrue();
		assertThat(this.client.getNamespace()).isEqualTo(this.properties.getNamespace());
		assertThat(this.service.read(null)).contains(this.properties.getConnectString());
	}

	@Test
	void createsReadsOverwritesAndDeletesNode() throws Exception {
		assertThat(this.service.read(this.nodeName)).isEmpty();
		assertThat(this.service.exists(this.nodeName)).isFalse();

		this.service.create(this.nodeName, "pc");
		assertThat(this.service.exists(this.nodeName)).isTrue();
		assertThat(this.service.read(this.nodeName)).contains("pc");

		this.service.create(this.nodeName, "pc-pro");
		assertThat(this.service.read(this.nodeName)).contains("pc-pro");

		this.service.create("other-" + this.nodeName, "keyboard");
		assertThat(this.service.children(null)).contains(this.nodeName, "other-" + this.nodeName);

		assertThat(this.service.delete(this.nodeName)).isTrue();
		assertThat(this.service.exists(this.nodeName)).isFalse();
		assertThat(this.service.read(this.nodeName)).isEmpty();
		assertThat(this.service.children(null)).doesNotContain(this.nodeName);
	}

	/**
	 * 删除带子节点的节点不会抛 {@code NotEmpty}:ZK 做不到递归删除,{@code deletingChildrenIfNeeded} 由 curator
	 * 逐层删掉。子节点用容器里的客户端直接建,绕开「节点名不能含 {@code /}」这层演示用约束。
	 */
	@Test
	void deletesNodeWithChildren() throws Exception {
		String parent = "parent-" + this.nodeName;
		String child = "child-" + this.nodeName;
		this.service.create(parent, "pc");
		// 子节点用容器里的客户端直接建在 parent 下面:service 的节点名不允许含 '/' 是演示接口的约束
		String childPath = this.service.absolutePath(parent) + "/" + child;
		this.client.create().forPath(childPath, "keyboard".getBytes(StandardCharsets.UTF_8));

		assertThat(this.client.checkExists().forPath(childPath)).isNotNull();
		assertThat(this.service.delete(parent)).isTrue();
		assertThat(this.client.checkExists().forPath(childPath)).isNull();
	}

	/**
	 * 删除不存在的节点返回 {@code false},而不是抛 {@code NoNodeException}.
	 */
	@Test
	void deleteReturnsFalseForMissingNode() throws Exception {
		assertThat(this.service.delete("missing-" + this.nodeName)).isFalse();
	}

	@Test
	void attributesComeFromApplicationYaml() {
		assertThat(this.properties.getConnectString()).isEqualTo("192.168.12.29:2181");
		assertThat(this.properties.getNamespace()).isEqualTo("sandbox-curator");
		assertThat(this.properties.getBasePath()).isEqualTo("/butterfly/sandbox/curator");
		assertThat(this.service.absolutePath(this.nodeName)).isEqualTo("/butterfly/sandbox/curator/" + this.nodeName);
	}

	/**
	 * 连接超时/会话超时支持 {@code 5s} 这种写法,测试类自身也用它把等待压短。
	 */
	@Test
	void timeoutsAreBoundAsDurations() {
		assertThat(this.properties.getConnectionTimeout()).isEqualTo(Duration.ofSeconds(5));
		assertThat(this.properties.getSessionTimeout()).isEqualTo(Duration.ofSeconds(10));
	}

	/**
	 * 直接用 curator 的原生入口读一次带 namespace 前缀的真实路径,确认沙箱的读写确实落在 {@code /<namespace><base-path>}
	 * 这棵子树上。
	 */
	@Test
	void namespacePrefixesRealZookeeperPath() throws Exception {
		this.service.create(this.nodeName, "pc");

		// service 的路径不带 namespace,可它确实写进了 ZK:用容器里带 namespace 的客户端按同一个相对路径能读到
		assertThat(this.service.exists(this.nodeName)).isTrue();
		assertThat(this.client.checkExists().forPath(this.service.absolutePath(this.nodeName))).isNotNull();

		// 同一路径落到 ZK 根上则什么都没有——namespace 是客户端拼的,不是节点名的一部分
		try (CuratorFramework freshClient = CuratorFrameworkFactory.newClient(this.properties.getConnectString(),
				(int) this.properties.getSessionTimeout().toMillis(),
				(int) this.properties.getConnectionTimeout().toMillis(), new ExponentialBackoffRetry(1000, 3))) {
			freshClient.start();
			assertThat(freshClient.checkExists().forPath(this.service.absolutePath(this.nodeName))).isNull();
			assertThat(freshClient.checkExists()
				.forPath("/" + this.properties.getNamespace() + this.service.absolutePath(this.nodeName))).isNotNull();
		}
	}

}
