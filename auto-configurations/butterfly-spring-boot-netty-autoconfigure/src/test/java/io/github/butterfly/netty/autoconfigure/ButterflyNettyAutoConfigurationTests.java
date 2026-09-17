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

import io.netty.channel.epoll.Epoll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class ButterflyNettyAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ButterflyNettyAutoConfiguration.class));

	@Test
	void startsServerWhenPropertiesAbsent() {
		this.contextRunner.run((context) -> {
			assertThat(context).hasSingleBean(NettyServer.class);
			assertThat(context).hasSingleBean(NettyChannelInitializer.class);
			assertThat(context).hasSingleBean(NettyTransportFactory.class);
			assertThat(context.getBean(NettyServer.class).isRunning()).isTrue();
		});
	}

	@Test
	void bindsRandomPortFromConfiguration() {
		this.contextRunner.withPropertyValues("butterfly.netty.host=127.0.0.1", "butterfly.netty.port=0")
			.run((context) -> {
				assertThat(context).getBean(NettyServerProperties.class)
					.hasFieldOrPropertyWithValue("host", "127.0.0.1")
					.hasFieldOrPropertyWithValue("port", 0);
				assertThat(context.getBean(NettyServer.class).getPort()).isPositive();
			});
	}

	@Test
	void defaultsToNioTransport() {
		this.contextRunner.withPropertyValues("butterfly.netty.port=0").run((context) -> {
			assertThat(context.getBean(NettyServerProperties.class).getTransport())
				.isEqualTo(NettyServerProperties.Transport.NIO);
			assertThat(context.getBean(NettyTransportFactory.class).transport())
				.isEqualTo(NettyServerProperties.Transport.NIO);
		});
	}

	@Test
	void requestedNativeTransportFallsBackToNioWhenUnavailable() {
		this.contextRunner.withPropertyValues("butterfly.netty.port=0", "butterfly.netty.transport=epoll")
			.run((context) -> {
				NettyServer server = context.getBean(NettyServer.class);
				assertThat(server.isRunning()).isTrue();
				// 平台支持 epoll 时使用 epoll,否则必须回退到 NIO 而不是启动失败
				assertThat(server.getTransport()).isEqualTo(Epoll.isAvailable() ? NettyServerProperties.Transport.EPOLL
						: NettyServerProperties.Transport.NIO);
			});
	}

	@Test
	void disabledSkipsAllBeans() {
		this.contextRunner.withPropertyValues("butterfly.netty.enabled=false").run((context) -> {
			assertThat(context).doesNotHaveBean(NettyServer.class);
			assertThat(context).doesNotHaveBean(NettyServerProperties.class);
			assertThat(context).doesNotHaveBean(NettyChannelInitializer.class);
		});
	}

	@Test
	void userDefinedChannelInitializerWins() {
		NettyChannelInitializer custom = (channel) -> {
		};
		this.contextRunner.withPropertyValues("butterfly.netty.port=0")
			.withBean(NettyChannelInitializer.class, () -> custom)
			.run((context) -> assertThat(context).getBean(NettyChannelInitializer.class).isSameAs(custom));
	}

	@Test
	void userDefinedServerWins() {
		this.contextRunner.withPropertyValues("butterfly.netty.port=0")
			.withUserConfiguration(CustomServerConfiguration.class)
			.run((context) -> {
				assertThat(context).hasSingleBean(NettyServer.class);
				assertThat(context.getBean(NettyServer.class)).isSameAs(CustomServerConfiguration.SERVER);
			});
	}

	@Configuration(proxyBeanMethods = false)
	static class CustomServerConfiguration {

		static final NettyServer SERVER = createServer();

		@Bean
		static NettyServer server() {
			return SERVER;
		}

		private static NettyServer createServer() {
			NettyServerProperties properties = new NettyServerProperties();
			properties.setHost("127.0.0.1");
			properties.setPort(0);
			return new NettyServer(properties, (channel) -> {
			}, new NioNettyTransportFactory());
		}

	}

}
