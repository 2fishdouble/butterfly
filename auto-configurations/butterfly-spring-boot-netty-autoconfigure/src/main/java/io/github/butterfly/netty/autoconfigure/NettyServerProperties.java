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

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Netty 服务器配置,前缀 {@code butterfly.netty}.
 * <p>
 * getter/setter 由 {@code @Data} 生成,交给 Spring Boot 完成松散绑定.
 */
@Data
@ConfigurationProperties(prefix = "butterfly.netty")
public class NettyServerProperties {

	/**
	 * 总开关.关闭后不注册任何 Netty 相关 bean,也不会启动服务器.
	 */
	private boolean enabled = true;

	/**
	 * 监听地址,默认监听所有网卡.
	 */
	private String host = "0.0.0.0";

	/**
	 * 监听端口;{@code 0} 表示由操作系统随机分配一个空闲端口.
	 */
	private int port = 9000;

	/**
	 * IO 模型,默认 {@link Transport#NIO}.选择 EPOLL / KQUEUE 时要求类路径上存在对应的 Netty 原生传输实现,
	 * 且运行平台支持该模型,否则自动回退到 NIO.
	 */
	private Transport transport = Transport.NIO;

	/**
	 * boss 线程数,只负责接收连接,默认 1 即可.
	 */
	private int bossThreads = 1;

	/**
	 * worker 线程数,负责连接上的读写;小于等于 0 时由 Netty 按 CPU 核数决定(2 * 核数).
	 */
	private int workerThreads = 0;

	/**
	 * 已完成三次握手但尚未被 accept 的连接队列长度.
	 */
	private int backlog = 128;

	/**
	 * 是否开启 TCP 心跳(child 通道 {@code SO_KEEPALIVE}).
	 */
	private boolean keepAlive = true;

	/**
	 * 是否禁用 Nagle 算法(child 通道 {@code TCP_NODELAY}),开启后可降低小包延迟.
	 */
	private boolean tcpNoDelay = true;

	/**
	 * 是否允许地址复用(server 通道 {@code SO_REUSEADDR}).
	 */
	private boolean reuseAddress = true;

	/**
	 * 读空闲超时(秒).小于等于 0 表示不检测;超时后由服务端主动关闭连接.
	 */
	private int readerIdleSeconds = 0;

	/**
	 * 单行报文最大长度(字节).超过该长度的报文会被丢弃并关闭连接,避免超长报文撑爆内存.
	 */
	private int maxFrameLength = 8192;

	/**
	 * 文本编解码字符集.
	 */
	private String charset = "UTF-8";

	/**
	 * IO 模型.
	 */
	public enum Transport {

		/**
		 * JDK 原生 NIO,跨平台,默认选项.
		 */
		NIO,

		/**
		 * Linux epoll,需要 Netty 原生传输支持.
		 */
		EPOLL,

		/**
		 * macOS / BSD kqueue,需要 Netty 原生传输支持.
		 */
		KQUEUE

	}

}
