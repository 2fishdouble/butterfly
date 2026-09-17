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

import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;

/**
 * 基于 JDK 原生 NIO 的 {@link NettyTransportFactory},跨平台可用,是默认实现.
 */
final class NioNettyTransportFactory implements NettyTransportFactory {

	@Override
	public NettyServerProperties.Transport transport() {
		return NettyServerProperties.Transport.NIO;
	}

	@Override
	public boolean isAvailable() {
		return true;
	}

	@Override
	public EventLoopGroup newEventLoopGroup(int threads, String poolName) {
		return new MultiThreadIoEventLoopGroup(threads, new DefaultThreadFactory(poolName), NioIoHandler.newFactory());
	}

	@Override
	public Class<? extends ServerChannel> serverChannelType() {
		return NioServerSocketChannel.class;
	}

}
