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
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.LineEncoder;
import io.netty.handler.codec.string.LineSeparator;
import io.netty.handler.codec.string.StringDecoder;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class NettyServerTests {

	private @Nullable NettyServer server;

	@AfterEach
	void stopServer() {
		if (this.server != null) {
			this.server.stop();
			this.server = null;
		}
	}

	@Test
	void startsAndStopsServerWithRandomPort() {
		this.server = createServer(new EchoChannelInitializer());

		this.server.start();

		assertThat(this.server.isRunning()).isTrue();
		assertThat(this.server.getPort()).isPositive();

		this.server.stop();

		assertThat(this.server.isRunning()).isFalse();
	}

	@Test
	void startIsIdempotent() {
		this.server = createServer(new EchoChannelInitializer());

		this.server.start();
		int port = this.server.getPort();
		this.server.start();

		assertThat(this.server.getPort()).isEqualTo(port);
	}

	@Test
	void echoesLineBackToClient() throws Exception {
		this.server = createServer(new EchoChannelInitializer());
		this.server.start();

		assertThat(roundTrip(this.server.getPort(), "butterfly")).isEqualTo("butterfly");
	}

	@Test
	void reusesSingleConnectionForMultipleLines() throws Exception {
		this.server = createServer(new EchoChannelInitializer());
		this.server.start();

		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress("127.0.0.1", this.server.getPort()), 5000);
			socket.setSoTimeout(5000);
			PrintWriter writer = new PrintWriter(
					new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
			BufferedReader reader = new BufferedReader(
					new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
			for (String message : new String[] { "first", "second", "third" }) {
				writer.print(message + "\r\n");
				writer.flush();
				assertThat(reader.readLine()).isEqualTo(message);
			}
		}
	}

	private NettyServer createServer(NettyChannelInitializer channelInitializer) {
		NettyServerProperties properties = new NettyServerProperties();
		properties.setHost("127.0.0.1");
		properties.setPort(0);
		return new NettyServer(properties, channelInitializer, new NioNettyTransportFactory());
	}

	private String roundTrip(int port, String message) throws Exception {
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress("127.0.0.1", port), 5000);
			socket.setSoTimeout(5000);
			PrintWriter writer = new PrintWriter(
					new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
			BufferedReader reader = new BufferedReader(
					new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
			writer.print(message + "\r\n");
			writer.flush();
			return reader.readLine();
		}
	}

	/**
	 * 回显连接装配器:按行解码后原样写回,用于验证 pipeline 与服务器生命周期.
	 */
	private static final class EchoChannelInitializer implements NettyChannelInitializer {

		@Override
		public void initChannel(SocketChannel channel) {
			channel.pipeline()
				.addLast(new LineBasedFrameDecoder(1024))
				.addLast(new StringDecoder(StandardCharsets.UTF_8))
				.addLast(new LineEncoder(LineSeparator.DEFAULT, StandardCharsets.UTF_8))
				.addLast(new SimpleChannelInboundHandler<String>() {

					@Override
					protected void channelRead0(ChannelHandlerContext context, String message) {
						context.writeAndFlush(message);
					}

				});
		}

	}

}
