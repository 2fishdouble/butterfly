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

package io.github.butterfly.netty.autoconfigure;

import io.netty.bootstrap.ServerBootstrap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Netty 自动配置:只要类路径上存在 Netty,就按 {@code butterfly.netty.*} 的配置启动一个 TCP 服务器.
 * <p>
 * 由 {@code butterfly.netty.enabled} 控制,缺省(未配置)时视为开启; 使用方自定义 {@link NettyServer} 或
 * {@link NettyChannelInitializer} bean 时直接覆盖对应实现。IO 模型默认 NIO,配置 EPOLL / KQUEUE 但当前平台
 * 或类路径不支持时自动回退到 NIO 并打印告警。
 */
@AutoConfiguration
@EnableConfigurationProperties(NettyServerProperties.class)
@ConditionalOnClass({ ServerBootstrap.class, NettyChannelInitializer.class })
@ConditionalOnProperty(prefix = "butterfly.netty", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ButterflyNettyAutoConfiguration {

	private static final Logger log = LoggerFactory.getLogger(ButterflyNettyAutoConfiguration.class);

	/**
	 * 默认的 pipeline 装配器:按换行符分帧的文本协议.
	 * @param properties 服务器配置
	 * @return 基于文本行协议的 {@link TextLineChannelInitializer} 实例
	 */
	@Bean
	@ConditionalOnMissingBean
	public NettyChannelInitializer nettyChannelInitializer(NettyServerProperties properties) {
		return new TextLineChannelInitializer(properties);
	}

	/**
	 * 默认的 NIO IO 模型工厂.
	 * @return 基于 JDK 原生 NIO 的 {@link NettyTransportFactory} 实例
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = "butterfly.netty", name = "transport", havingValue = "nio", matchIfMissing = true)
	public NettyTransportFactory nioNettyTransportFactory() {
		return new NioNettyTransportFactory();
	}

	/**
	 * 注册随容器生命周期启停的 Netty 服务器.
	 * @param properties 服务器配置
	 * @param channelInitializer 新连接的 pipeline 装配器
	 * @param transportFactoryProvider 可用的 IO 模型工厂,按配置与实际可用性择优选择
	 * @return 由给定参数构造的 {@link NettyServer} 实例
	 */
	@Bean
	@ConditionalOnMissingBean
	public NettyServer nettyServer(NettyServerProperties properties, NettyChannelInitializer channelInitializer,
			ObjectProvider<NettyTransportFactory> transportFactoryProvider) {
		NettyTransportFactory transportFactory = resolveTransportFactory(properties, transportFactoryProvider);
		return new NettyServer(properties, channelInitializer, transportFactory);
	}

	private NettyTransportFactory resolveTransportFactory(NettyServerProperties properties,
			ObjectProvider<NettyTransportFactory> transportFactoryProvider) {
		NettyTransportFactory transportFactory = transportFactoryProvider.getIfAvailable();
		if (transportFactory != null && transportFactory.isAvailable()) {
			return transportFactory;
		}
		if (transportFactory != null) {
			log.warn("Netty transport {} is not available on this platform, falling back to NIO.",
					transportFactory.transport());
		}
		else if (properties.getTransport() != NettyServerProperties.Transport.NIO) {
			log.warn("Netty transport {} requires the matching native transport on the classpath, falling back to NIO.",
					properties.getTransport());
		}
		return new NioNettyTransportFactory();
	}

	/**
	 * 配置 {@code butterfly.netty.transport=epoll} 且类路径存在 epoll 支持时注册 epoll IO 模型工厂.
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(name = "io.netty.channel.epoll.Epoll")
	@ConditionalOnProperty(prefix = "butterfly.netty", name = "transport", havingValue = "epoll")
	static class EpollTransportConfiguration {

		@Bean
		@ConditionalOnMissingBean
		NettyTransportFactory epollNettyTransportFactory() {
			return new EpollNettyTransportFactory();
		}

	}

	/**
	 * 配置 {@code butterfly.netty.transport=kqueue} 且类路径存在 kqueue 支持时注册 kqueue IO 模型工厂.
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(name = "io.netty.channel.kqueue.KQueue")
	@ConditionalOnProperty(prefix = "butterfly.netty", name = "transport", havingValue = "kqueue")
	static class KQueueTransportConfiguration {

		@Bean
		@ConditionalOnMissingBean
		NettyTransportFactory kqueueNettyTransportFactory() {
			return new KQueueNettyTransportFactory();
		}

	}

}
