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
import io.netty.channel.ServerChannel;

/**
 * IO 模型工厂:屏蔽 NIO 与原生传输(epoll / kqueue)在创建事件循环组和 server 通道上的差异.
 * <p>
 * 自动配置按 {@code butterfly.netty.transport} 选择实现:未配置或配置为 NIO 时使用
 * {@code NioNettyTransportFactory}; 配置为 EPOLL / KQUEUE 且类路径与平台均支持时使用对应的原生实现,否则回退到 NIO.
 * <p>
 * 需要其它 IO 模型(例如 io_uring)时,注册自定义的 {@link NettyTransportFactory} bean 即可。
 */
public interface NettyTransportFactory {

	/**
	 * 当前工厂对应的 IO 模型.
	 * @return 当前工厂对应的 IO 模型枚举值
	 */
	NettyServerProperties.Transport transport();

	/**
	 * 当前运行环境是否真的可以使用该 IO 模型.
	 * @return 可用返回 {@code true}
	 */
	boolean isAvailable();

	/**
	 * 创建事件循环组.
	 * @param threads 线程数;小于等于 0 时由 Netty 按 CPU 核数决定
	 * @param poolName 线程池名称,便于在日志与线程转储中识别
	 * @return 新创建的事件循环组,由调用方负责关闭
	 */
	EventLoopGroup newEventLoopGroup(int threads, String poolName);

	/**
	 * 当前 IO 模型对应的 server 通道类型.
	 * @return server 通道的 {@link Class}
	 */
	Class<? extends ServerChannel> serverChannelType();

}
