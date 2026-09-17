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

import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.LineEncoder;
import io.netty.handler.codec.string.LineSeparator;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.timeout.IdleStateHandler;

import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

/**
 * 默认的 {@link NettyChannelInitializer} 实现:装配一套「按换行符分帧的 UTF-8 文本」协议.
 * <p>
 * pipeline 依次为 {@link LineBasedFrameDecoder} → {@link StringDecoder} →
 * {@link LineEncoder} → {@link NettyServerHandler};配置了
 * {@code butterfly.netty.reader-idle-seconds} 时还会插入 {@link IdleStateHandler} 用于回收空闲连接。
 * <p>
 * 出站使用的换行符为当前平台的 {@link LineSeparator#DEFAULT},入站则同时兼容 {@code \n} 与 {@code \r\n}。
 * 需要其它协议(HTTP、自定义二进制等)时,请自行注册 {@link NettyChannelInitializer} bean 覆盖本实现。
 */
public class TextLineChannelInitializer implements NettyChannelInitializer {

	private final NettyServerProperties properties;

	/**
	 * 创建基于文本行的连接装配器.
	 * @param properties 服务器配置,提供报文长度上限、字符集与读空闲时间
	 */
	public TextLineChannelInitializer(NettyServerProperties properties) {
		this.properties = properties;
	}

	/**
	 * 为新连接装配文本行协议的 pipeline.
	 * @param channel 已完成注册的新连接
	 */
	@Override
	public void initChannel(SocketChannel channel) {
		ChannelPipeline pipeline = channel.pipeline();
		pipeline.addLast("lineDecoder", new LineBasedFrameDecoder(this.properties.getMaxFrameLength()));
		pipeline.addLast("stringDecoder", new StringDecoder(charset()));
		pipeline.addLast("lineEncoder", new LineEncoder(LineSeparator.DEFAULT, charset()));
		if (this.properties.getReaderIdleSeconds() > 0) {
			pipeline.addLast("idleStateHandler",
					new IdleStateHandler(this.properties.getReaderIdleSeconds(), 0, 0, TimeUnit.SECONDS));
		}
		pipeline.addLast("handler", new NettyServerHandler());
	}

	private Charset charset() {
		return Charset.forName(this.properties.getCharset());
	}

}
