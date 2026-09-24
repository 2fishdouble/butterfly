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

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * curator 客户端配置,前缀 {@code butterfly.curator}.
 * <p>
 * 只覆盖「怎么连上 ZooKeeper」这一层,重试策略沿用 curator 默认的指数退避。常用配置项:
 * <ul>
 * <li>{@link #connectString}:ZK 地址,单机形如 {@code 192.168.12.29:2181},集群逗号分隔;</li>
 * <li>{@link #namespace}:curator 会把它拼成 {@code /<namespace>} 作为所有节点的公共前缀,应用之间借此互不干扰;</li>
 * <li>{@link #basePath}:演示节点的根路径,启动时幂等建出;</li>
 * <li>{@link #autoStartup}:设为 {@code false} 时只注册配置、不建连,沙箱测试据此在无 ZK 的机器上验证装配。</li>
 * </ul>
 */
@Data
@ConfigurationProperties(prefix = "butterfly.curator")
public class CuratorProperties {

	/**
	 * ZooKeeper 连接串,形如 {@code 192.168.12.29:2181};集群用逗号分隔多个地址,例如
	 * {@code host1:2181,host2:2181,host3:2181}.
	 */
	private String connectString = "127.0.0.1:2181";

	/**
	 * 命名空间:curator 会把它拼成 {@code /<命名空间>} 作为所有操作的公共前缀;留空表示不加前缀.
	 */
	private String namespace = "";

	/**
	 * 会话超时:客户端与 ZK 断开后,服务端在该时间内仍保留会话(含临时节点与 watch);过短会频繁掉线,过长会延迟故障感知.
	 * <p>
	 * 取值必须在 ZK 服务端的 {@code minSessionTimeout} 与 {@code maxSessionTimeout} 之间(默认 2 秒 ~ 20
	 * 秒的倍数), 超出范围时服务端会静默钳到边界值,所以这里默认给 60 秒。
	 */
	private Duration sessionTimeout = Duration.ofSeconds(60);

	/**
	 * 连接超时:建连阶段的最长等待时间;直连单机 ZK 时 15 秒足够,跨机房或集群可适当加大.
	 */
	private Duration connectionTimeout = Duration.ofSeconds(15);

	/**
	 * 演示节点根路径,必须是以 {@code /} 开头的绝对路径.
	 */
	private String basePath = "/butterfly/sandbox/curator";

	/**
	 * 是否注册并启动 {@link org.apache.curator.framework.CuratorFramework};设为 {@code false}
	 * 时容器里没有客户端, 沙箱测试据此在本地没有 ZK 的环境下验证配置绑定与路径拼装. 注意它不控制根路径的创建:开启时在启动阶段幂等建出,
	 * 关闭时任何读写都会提示未连接.
	 */
	private boolean autoStartup = true;

	/**
	 * ZK 鉴权信息,格式为 {@code scheme:auth},例如 {@code digest:user:password};留空表示不鉴权.
	 * <p>
	 * 走的是 ZooKeeper 原生的 {@code addAuthInfo}(digest 认证在明文连接上传输,只适合内网可信网络),不是 TLS。
	 */
	private String auth = "";

}
