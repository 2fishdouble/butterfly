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

package io.github.butterfly.rocketmq.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 校验 {@link ButterflyRocketMqAutoConfiguration}:兜底的主题管理器、启动时的建主题动作,以及使用方自定义时退让。
 * <p>
 * 全程不触网:默认的主题管理器只记日志;显式配置 {@code rocketmq.name-server} 时注册的是
 * {@link ClassicRocketMqTopicAdmin},但它的生产者是惰性启动的,而这里没有任何主题定义,所以永远不会启动客户端。
 */
class ButterflyRocketMqAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ButterflyRocketMqAutoConfiguration.class));

	/**
	 * 默认的兜底实现必须是只记日志的那个:RocketMQ 5 的 gRPC 客户端没有管理 API,不该假装能建主题.
	 */
	@Test
	void registersLoggingTopicAdminByDefault() {
		this.contextRunner.run((context) -> {
			assertThat(context).hasSingleBean(RocketMqTopicAdmin.class);
			assertThat(context.getBean(RocketMqTopicAdmin.class)).isInstanceOf(LoggingRocketMqTopicAdmin.class);
			assertThat(context).hasSingleBean(RocketMqTopicInitializer.class);
		});
	}

	/**
	 * 没有标注 {@link EnableRocketMqTemplates} 时主题定义为空,初始化器什么也不做.
	 */
	@Test
	void initializerIsHarmlessWithoutAnyEntityClass() {
		this.contextRunner.run((context) -> {
			RocketMqTopicInitializer initializer = context.getBean(RocketMqTopicInitializer.class);

			// 已经由容器调用过 afterSingletonsInstantiated,这里再调一次也不应抛异常
			initializer.afterSingletonsInstantiated();

			assertThat(context).doesNotHaveBean(RocketMqProperties.TopicDefinition.class);
		});
	}

	/**
	 * 使用方自定义主题管理器时本配置必须退让,否则会一次建两遍主题.
	 */
	@Test
	void backsOffWhenUserDefinesTopicAdmin() {
		this.contextRunner.withUserConfiguration(CustomTopicAdminConfiguration.class).run((context) -> {
			assertThat(context).hasSingleBean(RocketMqTopicAdmin.class);
			assertThat(context.getBean(RocketMqTopicAdmin.class)).isSameAs(CustomTopicAdminConfiguration.ADMIN);
		});
	}

	/**
	 * 同时配了 {@code rocketmq.name-server} 且类路径上存在老版 remoting 客户端时,换成真正建主题的实现.
	 * <p>
	 * 主题定义为空,所以它的生产者不会被启动;这里只断言类型,不触发任何网络调用。
	 */
	@Test
	void usesClassicTopicAdminWhenNameServerIsConfigured() {
		this.contextRunner.withPropertyValues("rocketmq.name-server=127.0.0.1:9876").run((context) -> {
			assertThat(context).hasSingleBean(RocketMqTopicAdmin.class);
			assertThat(context.getBean(RocketMqTopicAdmin.class)).isInstanceOf(ClassicRocketMqTopicAdmin.class);
		});
	}

	/**
	 * 使用方自定义主题管理器时,即使配了 NameServer 也不接管.
	 */
	@Test
	void userTopicAdminStillWinsOverClassicOne() {
		this.contextRunner.withUserConfiguration(CustomTopicAdminConfiguration.class)
			.withPropertyValues("rocketmq.name-server=127.0.0.1:9876")
			.run((context) -> assertThat(context.getBean(RocketMqTopicAdmin.class))
				.isSameAs(CustomTopicAdminConfiguration.ADMIN));
	}

	@Configuration(proxyBeanMethods = false)
	static class CustomTopicAdminConfiguration {

		static final RocketMqTopicAdmin ADMIN = new RecordingTopicAdmin();

		@Bean
		RocketMqTopicAdmin customTopicAdmin() {
			return ADMIN;
		}

	}

	static class RecordingTopicAdmin implements RocketMqTopicAdmin {

		private final List<String> createdTopics = new ArrayList<>();

		@Override
		public void createTopic(String topic, int queueCount) {
			this.createdTopics.add(topic);
		}

		List<String> createdTopics() {
			return List.copyOf(this.createdTopics);
		}

	}

}
