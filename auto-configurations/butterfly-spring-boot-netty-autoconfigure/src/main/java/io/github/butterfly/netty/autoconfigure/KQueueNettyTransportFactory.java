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
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueIoHandler;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;

/**
 * 基于 macOS / BSD kqueue 的 {@link NettyTransportFactory}.
 * <p>
 * 该实现只在 {@code io.netty.channel.kqueue.KQueue} 存在时才会被加载,且创建事件循环前会通过
 * {@link KQueue#isAvailable()} 确认原生库可用,不可用时由自动配置回退到 NIO。
 */
final class KQueueNettyTransportFactory implements NettyTransportFactory {

	@Override
	public NettyServerProperties.Transport transport() {
		return NettyServerProperties.Transport.KQUEUE;
	}

	@Override
	public boolean isAvailable() {
		return KQueue.isAvailable();
	}

	@Override
	public EventLoopGroup newEventLoopGroup(int threads, String poolName) {
		return new MultiThreadIoEventLoopGroup(threads, new DefaultThreadFactory(poolName),
				KQueueIoHandler.newFactory());
	}

	@Override
	public Class<? extends ServerChannel> serverChannelType() {
		return KQueueServerSocketChannel.class;
	}

}
