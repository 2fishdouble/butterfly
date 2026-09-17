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

import io.netty.channel.socket.SocketChannel;

/**
 * 连接装配扩展点:为新接入的连接装配 {@code ChannelPipeline}.
 * <p>
 * 使用方只要注册自己的 {@link NettyChannelInitializer} bean,即可完全接管协议编解码;若不注册,则由自动配置
 * 提供基于换行符分隔的文本协议实现 {@link TextLineChannelInitializer}。
 *
 * @see TextLineChannelInitializer
 */
@FunctionalInterface
public interface NettyChannelInitializer {

	/**
	 * 为新接入的连接装配 pipeline.
	 * <p>
	 * 该方法运行在 worker 线程上,只应做 pipeline 装配,不要执行阻塞操作。
	 * @param channel 已完成注册的新连接
	 */
	void initChannel(SocketChannel channel);

}
