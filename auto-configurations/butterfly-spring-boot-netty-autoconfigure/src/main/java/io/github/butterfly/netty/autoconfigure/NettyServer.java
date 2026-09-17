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
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 随 Spring 容器生命周期自动启停的 Netty 服务器.
 * <p>
 * 容器刷新时绑定 {@code butterfly.netty.host:butterfly.netty.port} 并开始接受连接,容器关闭时先关闭 server 通道
 * 再优雅关闭事件循环组。IO 模型由 {@link NettyTransportFactory} 决定,默认 NIO。
 */
public class NettyServer implements SmartLifecycle {

	private static final Logger log = LoggerFactory.getLogger(NettyServer.class);

	private static final long SHUTDOWN_TIMEOUT_SECONDS = 5;

	private final NettyServerProperties properties;

	private final NettyChannelInitializer channelInitializer;

	private final NettyTransportFactory transportFactory;

	private final AtomicBoolean running = new AtomicBoolean(false);

	private volatile @Nullable EventLoopGroup bossGroup;

	private volatile @Nullable EventLoopGroup workerGroup;

	private volatile @Nullable Channel serverChannel;

	/**
	 * 创建 Netty 服务器.
	 * @param properties 服务器配置
	 * @param channelInitializer 新连接的 pipeline 装配器
	 * @param transportFactory 决定事件循环组与 server 通道类型的 IO 模型工厂
	 */
	public NettyServer(NettyServerProperties properties, NettyChannelInitializer channelInitializer,
			NettyTransportFactory transportFactory) {
		this.properties = properties;
		this.channelInitializer = channelInitializer;
		this.transportFactory = transportFactory;
	}

	/**
	 * 启动服务器:创建事件循环组并绑定监听地址.
	 * <p>
	 * 绑定失败时关闭已创建的事件循环组并抛出 {@link IllegalStateException},让容器启动直接失败而不是带病运行。
	 * @throws IllegalStateException 端口被占用、地址不可用或启动过程被中断时抛出
	 */
	@Override
	public void start() {
		if (!this.running.compareAndSet(false, true)) {
			return;
		}
		EventLoopGroup boss = this.transportFactory.newEventLoopGroup(this.properties.getBossThreads(),
				"butterfly-netty-boss");
		EventLoopGroup worker = this.transportFactory.newEventLoopGroup(this.properties.getWorkerThreads(),
				"butterfly-netty-worker");
		try {
			ServerBootstrap bootstrap = new ServerBootstrap().group(boss, worker)
				.channel(this.transportFactory.serverChannelType())
				.option(ChannelOption.SO_BACKLOG, this.properties.getBacklog())
				.option(ChannelOption.SO_REUSEADDR, this.properties.isReuseAddress())
				.childOption(ChannelOption.SO_KEEPALIVE, this.properties.isKeepAlive())
				.childOption(ChannelOption.TCP_NODELAY, this.properties.isTcpNoDelay())
				.childHandler(new ChannelInitializer<SocketChannel>() {
					@Override
					protected void initChannel(SocketChannel channel) {
						NettyServer.this.channelInitializer.initChannel(channel);
					}
				});
			this.serverChannel = bootstrap.bind(this.properties.getHost(), this.properties.getPort()).sync().channel();
			this.bossGroup = boss;
			this.workerGroup = worker;
			log.info("Butterfly Netty server started on {}:{} with {} transport.", this.properties.getHost(), getPort(),
					this.transportFactory.transport());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			shutdown(boss, worker);
			this.running.set(false);
			throw new IllegalStateException("Interrupted while starting the Netty server", ex);
		}
		catch (Exception ex) {
			shutdown(boss, worker);
			this.running.set(false);
			throw new IllegalStateException("Failed to start the Netty server on " + this.properties.getHost() + ":"
					+ this.properties.getPort(), ex);
		}
	}

	/**
	 * 停止服务器:关闭 server 通道并优雅关闭事件循环组.
	 */
	@Override
	public void stop() {
		if (!this.running.compareAndSet(true, false)) {
			return;
		}
		Channel channel = this.serverChannel;
		this.serverChannel = null;
		if (channel != null) {
			channel.close().syncUninterruptibly();
		}
		EventLoopGroup boss = this.bossGroup;
		EventLoopGroup worker = this.workerGroup;
		this.bossGroup = null;
		this.workerGroup = null;
		shutdown(boss, worker);
		log.info("Butterfly Netty server stopped.");
	}

	/**
	 * 服务器是否处于运行状态.
	 * @return 已启动且未停止时返回 {@code true}
	 */
	@Override
	public boolean isRunning() {
		return this.running.get();
	}

	/**
	 * 返回服务器实际监听的端口.
	 * <p>
	 * 配置 {@code butterfly.netty.port=0} 时,端口由操作系统分配,可通过本方法读取真实端口。
	 * @return 已绑定端口;尚未启动时返回配置的端口
	 */
	public int getPort() {
		Channel channel = this.serverChannel;
		if (channel != null && channel.localAddress() instanceof InetSocketAddress address) {
			return address.getPort();
		}
		return this.properties.getPort();
	}

	/**
	 * 返回服务器实际使用的 IO 模型.
	 * <p>
	 * 配置了 EPOLL / KQUEUE 但当前平台不支持时,这里返回的是回退后的 NIO。
	 * @return 生效的 IO 模型
	 */
	public NettyServerProperties.Transport getTransport() {
		return this.transportFactory.transport();
	}

	private void shutdown(@Nullable EventLoopGroup boss, @Nullable EventLoopGroup worker) {
		shutdownGroup(worker);
		shutdownGroup(boss);
	}

	private void shutdownGroup(@Nullable EventLoopGroup group) {
		if (group == null) {
			return;
		}
		try {
			group.shutdownGracefully(0, SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS).syncUninterruptibly();
		}
		catch (RuntimeException ex) {
			log.warn("Failed to shut down the Netty event loop group: {}", ex.getMessage());
		}
	}

}
