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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认的入站处理器:仅记录收到的文本报文,并在连接空闲或异常时关闭连接.
 * <p>
 * 它由 {@link TextLineChannelInitializer} 装配,只作为「服务器已启动且能收包」的兜底实现; 真正的业务处理请在
 * {@link NettyChannelInitializer} 中自行装配处理器。
 */
class NettyServerHandler extends SimpleChannelInboundHandler<String> {

	private static final Logger log = LoggerFactory.getLogger(NettyServerHandler.class);

	/**
	 * 连接建立时记录远端地址.
	 * @param ctx 当前连接的处理上下文
	 */
	@Override
	public void channelActive(ChannelHandlerContext ctx) {
		log.debug("Netty connection established: {}", ctx.channel().remoteAddress());
	}

	/**
	 * 记录收到的单行文本报文.
	 * @param ctx 当前连接的处理上下文
	 * @param msg 已按换行符分帧并解码后的文本
	 */
	@Override
	protected void channelRead0(ChannelHandlerContext ctx, String msg) {
		log.info("Netty server received: {}", msg);
	}

	/**
	 * 读空闲超时后主动关闭连接,避免连接长期占用.
	 * @param ctx 当前连接的处理上下文
	 * @param evt 触发的事件
	 * @throws Exception 交回父类处理非空闲事件时可能抛出的异常
	 */
	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		if (evt instanceof IdleStateEvent) {
			log.debug("Closing idle Netty connection: {}", ctx.channel().remoteAddress());
			ctx.close();
			return;
		}
		super.userEventTriggered(ctx, evt);
	}

	/**
	 * 记录异常并关闭连接,避免坏连接继续占用 worker 线程.
	 * @param ctx 当前连接的处理上下文
	 * @param cause 触发关闭的异常
	 */
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		log.warn("Closing Netty connection {} because of an error: {}", ctx.channel().remoteAddress(),
				cause.getMessage());
		ctx.close();
	}

}
