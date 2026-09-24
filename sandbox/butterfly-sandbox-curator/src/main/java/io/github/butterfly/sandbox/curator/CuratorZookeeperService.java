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

package io.github.butterfly.sandbox.curator;

import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.imps.CuratorFrameworkState;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * 用 curator 读写 ZooKeeper 的演示组件:把「保证根路径存在」与增删改查包成几个直白的方法,供 {@link CuratorController} 暴露成
 * HTTP 接口.
 * <p>
 * 路径处理的两条约定:
 * <ul>
 * <li>{@code name} 是相对 {@code butterfly.curator.base-path} 的节点名,只允许一段(不能含
 * {@code /});</li>
 * <li>客户端带 namespace 时,写入 ZK 的真实路径是 {@code /<namespace>/<basePath>/<name>},而
 * {@link #absolutePath(String)} 返回的是不带 namespace 的那一段,便于和 {@code zkCli} 对照。</li>
 * </ul>
 * {@code butterfly.curator.auto-startup=false} 时容器里没有
 * {@link CuratorFramework}:此时本组件自己建一个不启动的 客户端,只用它来读 {@code connectString}
 * 等配置,因此应用仍能启动,只是任何读写都会提示未连接。
 */
@Slf4j
@Component
public class CuratorZookeeperService implements InitializingBean {

	private final CuratorFramework client;

	/**
	 * 客户端是否由本组件自己创建(即没拿到容器里的 bean),自己的客户端要自己关.
	 */
	private final boolean clientOwned;

	private final String connectString;

	private final String namespace;

	private final String basePath;

	/**
	 * 容器里有 {@link CuratorFramework} 时直接用它;没有({@code auto-startup=false})时按同样的配置建一个不启动的
	 * 客户端,仅用于读取 {@code connectString} 与 {@code namespace};未启动的客户端也能安全地读这些属性.
	 * @param properties 连接配置
	 * @param client 容器里的 curator 客户端,可能不存在
	 */
	public CuratorZookeeperService(CuratorProperties properties, @Nullable CuratorFramework client) {
		this.clientOwned = (client == null);
		this.client = (client != null) ? client : newClient(properties);
		this.connectString = properties.getConnectString();
		this.namespace = properties.getNamespace();
		this.basePath = properties.getBasePath();
	}

	/**
	 * 自建客户端:走 {@link CuratorFrameworkFactory#newClient} 这个重载,它会填好重试策略与默认值, 省得再去拼 builder
	 * 的必填项;这里刻意<b>不</b>调用 {@code start()},保证不会真的去连 ZK.
	 * @param properties 连接配置
	 * @return 尚未启动的 curator 客户端
	 */
	private static CuratorFramework newClient(CuratorProperties properties) {
		return CuratorFrameworkFactory.newClient(properties.getConnectString(),
				(int) properties.getSessionTimeout().toMillis(), (int) properties.getConnectionTimeout().toMillis(),
				new ExponentialBackoffRetry(1000, 3));
	}

	/**
	 * 启动时幂等地建出根路径:节点已存在时不报错,只把值覆盖成当前配置.
	 * @throws Exception 连接不可用或创建失败
	 */
	@Override
	public void afterPropertiesSet() throws Exception {
		if (this.clientOwned) {
			log.warn("no CuratorFramework bean (butterfly.curator.auto-startup=false), "
					+ "zookeeper operations will report that the client is not connected");
			return;
		}
		ensureBasePath();
	}

	/**
	 * 幂等创建根路径,并把值写成 {@code connectString},便于用 {@code zkCli} 一眼看出是哪个应用建的.
	 * <p>
	 * 根路径是绝对路径,不走 {@link #path(String)} 的「相对节点名」校验,只做存在性判断与创建/覆盖。
	 * @throws Exception 连接不可用
	 */
	public void ensureBasePath() throws Exception {
		if (this.client.checkExists().forPath(this.basePath) == null) {
			this.client.create()
				.creatingParentsIfNeeded()
				.withMode(CreateMode.PERSISTENT)
				.forPath(this.basePath, this.connectString.getBytes(StandardCharsets.UTF_8));
			return;
		}
		this.client.setData().forPath(this.basePath, this.connectString.getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * 创建或覆盖一个持久节点.
	 * @param name 相对根路径的节点名
	 * @param value 节点值,按 UTF-8 编码
	 * @throws Exception 节点名非法、父节点无法创建或连接不可用
	 */
	public void create(String name, String value) throws Exception {
		String path = path(name);
		if (this.client.checkExists().forPath(path) == null) {
			this.client.create()
				.creatingParentsIfNeeded()
				.withMode(CreateMode.PERSISTENT)
				.forPath(path, value.getBytes(StandardCharsets.UTF_8));
			return;
		}
		// 已存在:覆盖值,等价于「幂等创建」,重复执行不会抛 NodeExistsException
		this.client.setData().forPath(path, value.getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * 节点是否存在.
	 * @param name 相对根路径的节点名,{@code null} 或空串表示根路径自身
	 * @return 存在时为 {@code true}
	 * @throws Exception 节点名非法或连接不可用
	 */
	public boolean exists(@Nullable String name) throws Exception {
		return this.client.checkExists().forPath(path(name)) != null;
	}

	/**
	 * 读取节点值.
	 * @param name 相对根路径的节点名
	 * @return 节点值的 UTF-8 字符串;节点不存在时为空 {@link Optional},不抛异常
	 * @throws Exception 节点名非法或连接不可用
	 */
	public Optional<String> read(@Nullable String name) throws Exception {
		String path = path(name);
		try {
			byte[] data = this.client.getData().forPath(path);
			return Optional.of(new String(data, StandardCharsets.UTF_8));
		}
		catch (KeeperException.NoNodeException ex) {
			// 存在性检查与实际读取之间被别的客户端删掉了,这里按「不存在」处理
			return Optional.empty();
		}
	}

	/**
	 * 删除节点;有子节点时一并删除.
	 * @param name 相对根路径的节点名
	 * @return 本次是否真的删掉了节点(不存在时为 {@code false})
	 * @throws Exception 节点名非法或连接不可用
	 */
	public boolean delete(String name) throws Exception {
		String path = path(name);
		if (this.client.checkExists().forPath(path) == null) {
			return false;
		}
		this.client.delete().deletingChildrenIfNeeded().forPath(path);
		return true;
	}

	/**
	 * 列出直接子节点名.
	 * @param name 相对根路径的节点名,{@code null} 或空串表示根路径自身
	 * @return 子节点名(不含路径前缀)的不可变列表,节点不存在时为空列表
	 * @throws Exception 节点名非法或连接不可用
	 */
	public List<String> children(@Nullable String name) throws Exception {
		String path = path(name);
		try {
			return List.copyOf(this.client.getChildren().forPath(path));
		}
		catch (KeeperException.NoNodeException ex) {
			return List.of();
		}
	}

	/**
	 * 客户端自身的生命周期状态,只会是 {@code LATENT}/{@code STARTED}/{@code STOPPED}:它说明的是「客户端有没有启动」,
	 * 不说明「连没连上 ZK」,判断后者请用 {@link #connected()}.
	 * @return curator 客户端状态
	 */
	public CuratorFrameworkState state() {
		return this.client.getState();
	}

	/**
	 * 会话是否已经建立:curator 的 {@code getState()} 只反映客户端启没启动,这里用底层 ZooKeeper 客户端的连接状态,
	 * 语义与「连上了没有」一致,也适合做健康检查.
	 * @return 已建立会话时为 {@code true};客户端未启动或正在重连时为 {@code false}
	 */
	public boolean connected() {
		return this.client.getZookeeperClient().isConnected();
	}

	/**
	 * 与 ZooKeeper 的连接串,即 {@code application.yml} 里的
	 * {@code butterfly.curator.connect-string}.
	 * @return 连接串
	 */
	public String connectString() {
		return this.connectString;
	}

	/**
	 * curator 命名空间;未配置时为空串.
	 * @return 命名空间
	 */
	public String namespace() {
		return this.namespace;
	}

	/**
	 * 演示节点根路径(不含 namespace).
	 * @return 根路径
	 */
	public String basePath() {
		return this.basePath;
	}

	/**
	 * 根路径下的绝对路径(不含 namespace).
	 * @param name 相对根路径的节点名,{@code null} 或空串表示根路径自身
	 * @return 形如 {@code /butterfly/sandbox/curator/computer-1} 的路径
	 */
	public String absolutePath(@Nullable String name) {
		return path(name);
	}

	/**
	 * 相对节点名 → 绝对路径.节点名为空({@code null} 或空串)时表示根路径;含 {@code /} 时直接拒绝, 保证演示接口只操作
	 * {@code butterfly.curator.base-path} 这一棵子树.
	 * @param name 相对根路径的节点名,{@code null} 或空串表示根路径自身
	 * @return 节点绝对路径(不含 namespace)
	 */
	private String path(@Nullable String name) {
		String node = (name != null) ? name : "";
		String base = this.basePath.endsWith("/") ? this.basePath.substring(0, this.basePath.length() - 1)
				: this.basePath;
		if (node.isEmpty()) {
			return base;
		}
		if (node.indexOf('/') >= 0) {
			throw new IllegalArgumentException("node name must not contain '/': " + node);
		}
		return base + "/" + node;
	}

}
