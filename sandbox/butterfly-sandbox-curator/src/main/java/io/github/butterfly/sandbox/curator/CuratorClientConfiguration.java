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
import org.apache.curator.framework.AuthInfo;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * curator 沙箱的装配:把 {@code butterfly.curator.*} 变成容器里唯一的 {@link CuratorFramework} bean.
 * <p>
 * 只做「建客户端」:命名空间、会话/连接超时与鉴权来自 {@link CuratorProperties},重试策略用 curator 惯用的指数退避 (1 秒起、最多 3
 * 次)。建连是异步的——{@code start()} 立刻返回,后台线程去连 ZK,这样 ZK 暂时不可用时应用仍能启动, 由 curator
 * 负责重连;需要「启动就必须连上」的场景,应改为同步等待(见 {@code blockUntilConnected})并显式处理失败。
 * <p>
 * {@code butterfly.curator.auto-startup=false} 时整个配置不生效,容器里没有现成的客户端:沙箱测试只能验证配置绑定,
 * 不会因为本机没有 ZK 而失败(配置绑定由 {@link CuratorPropertiesConfiguration} 负责,不受该开关影响)。此时
 * {@link CuratorZookeeperService} 会自己建一个只用于读配置的客户端。
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "butterfly.curator", name = "auto-startup", matchIfMissing = true)
public class CuratorClientConfiguration {

	/**
	 * 重试策略与 curator 官方示例一致:退避 1 秒起,最多重试 3 次.
	 */
	private static final int BASE_SLEEP_TIME_MS = 1000;

	private static final int MAX_RETRIES = 3;

	/**
	 * 构造并启动客户端.bean 销毁时由 curator 自己的 {@code close()} 关闭会话,不再单独声明 destroyMethod.
	 * @param properties 连接配置
	 * @return 已启动(建连在后台进行)的 curator 客户端
	 */
	@Bean(destroyMethod = "close")
	public CuratorFramework curatorFramework(CuratorProperties properties) {
		String connectString = properties.getConnectString();
		int sessionTimeoutMs = (int) properties.getSessionTimeout().toMillis();
		int connectionTimeoutMs = (int) properties.getConnectionTimeout().toMillis();
		// 需要鉴权时才走 builder:CuratorFrameworkFactory.newClient 不支持 auth
		if (properties.getAuth().isBlank()) {
			return startNewClient(connectString, sessionTimeoutMs, connectionTimeoutMs, properties, null);
		}
		// 形如 digest:user:password,scheme 在前,其余部分(可能还含冒号)整体作为 auth
		String[] parts = properties.getAuth().split(":", 2);
		List<AuthInfo> auth = List.of(new AuthInfo(parts[0], parts[1].getBytes(StandardCharsets.UTF_8)));
		return startNewClient(connectString, sessionTimeoutMs, connectionTimeoutMs, properties, auth);
	}

	private CuratorFramework startNewClient(String connectString, int sessionTimeoutMs, int connectionTimeoutMs,
			CuratorProperties properties, @Nullable List<AuthInfo> auth) {
		CuratorFrameworkFactory.Builder builder = CuratorFrameworkFactory.builder()
			.connectString(connectString)
			.sessionTimeoutMs(sessionTimeoutMs)
			.connectionTimeoutMs(connectionTimeoutMs)
			// curator 5.x 的 builder 不再提供默认重试策略,必须显式给出
			.retryPolicy(new ExponentialBackoffRetry(BASE_SLEEP_TIME_MS, MAX_RETRIES));
		if (!properties.getNamespace().isBlank()) {
			builder.namespace(properties.getNamespace());
		}
		if (auth != null) {
			builder.authorization(auth);
		}
		CuratorFramework client = builder.build();
		client.start();
		log.info("curator client started: connectString={}, namespace={}, basePath={}", connectString,
				properties.getNamespace(), properties.getBasePath());
		return client;
	}

}
