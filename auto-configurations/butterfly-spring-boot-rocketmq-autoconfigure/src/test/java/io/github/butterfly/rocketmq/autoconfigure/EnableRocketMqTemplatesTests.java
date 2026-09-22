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

import org.apache.rocketmq.client.core.RocketMQClientTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 校验 {@link EnableRocketMqTemplates} 按实体类声明主题定义与绑定好主题的发送器,以及
 * {@link RocketMqTopicInitializer} 只在需要时建主主题。全程不触网:发送器只在构造时拿到共享模板,不会被调用。
 */
class EnableRocketMqTemplatesTests {

	/**
	 * 同时装配本模块的自动配置:建主题的初始化器由它注册,测试才能断言"启动时建了哪些主题".
	 */
	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ButterflyRocketMqAutoConfiguration.class))
		.withUserConfiguration(TemplateConfiguration.class);

	/**
	 * 每个实体类都要拿到一份主题定义与一个发送器,命名规则是实体类简单名首字母小写后拼后缀.
	 */
	@Test
	void registersTopicDefinitionAndSenderForEveryEntityClass() {
		this.contextRunner.withUserConfiguration(SampleConfiguration.class).run((context) -> {
			assertThat(context).hasBean("computerRocketMqTopic")
				.hasBean("computerRocketMqSender")
				.hasBean("orderRocketMqTopic")
				.hasBean("orderRocketMqSender");

			RocketMqSender sender = context.getBean("computerRocketMqSender", RocketMqSender.class);
			assertThat(sender.topic()).isEqualTo("computer");
			assertThat(sender.consumerGroup()).isEqualTo("computer");
			assertThat(sender.deadLetterTopic()).isEqualTo("%DLQ%computer");
			assertThat(sender.retryTopic()).isEqualTo("%RETRY%computer");
		});
	}

	/**
	 * 所有发送器复用容器中唯一的那一个模板:不是每个实体类各建一份 Producer 与连接.
	 */
	@Test
	void everySenderReusesTheSingleSharedTemplate() {
		this.contextRunner.withUserConfiguration(SampleConfiguration.class).run((context) -> {
			assertThat(context).hasSingleBean(RocketMQClientTemplate.class);

			Object shared = context.getBean(RocketMQClientTemplate.class);
			assertThat(ReflectionTestUtils.getField(context.getBean("computerRocketMqSender"), "template"))
				.isSameAs(shared);
			assertThat(ReflectionTestUtils.getField(context.getBean("orderRocketMqSender"), "template"))
				.isSameAs(shared);
		});
	}

	/**
	 * 主题名、消费组名与死信主题名都要来自配置.
	 */
	@Test
	void configuredNamesAreUsed() {
		this.contextRunner.withUserConfiguration(SampleConfiguration.class)
			.withPropertyValues("butterfly.rocketmq.entities.computer.topic=butterfly-computer",
					"butterfly.rocketmq.entities.computer.consumer-group=butterfly-computer-group")
			.run((context) -> {
				RocketMqSender sender = context.getBean("computerRocketMqSender", RocketMqSender.class);

				assertThat(sender.topic()).isEqualTo("butterfly-computer");
				assertThat(sender.consumerGroup()).isEqualTo("butterfly-computer-group");
				assertThat(sender.deadLetterTopic()).isEqualTo("%DLQ%butterfly-computer-group");
			});
	}

	/**
	 * 配置了标签后,发送目的地要带上 {@code topic:tags},否则过滤消费收不到消息.
	 */
	@Test
	void destinationCarriesConfiguredTag() {
		this.contextRunner.withUserConfiguration(SampleConfiguration.class)
			.withPropertyValues("butterfly.rocketmq.entities.computer.tag=created")
			.run((context) -> assertThat(
					context.getBean("computerRocketMqTopic", RocketMqProperties.TopicDefinition.class).destination())
				.isEqualTo("computer:created"));
	}

	/**
	 * 启动时要为每个开启了建主题开关的实体类创建主主题.
	 */
	@Test
	void createsMainTopicsOnStartup() {
		this.contextRunner.withUserConfiguration(SampleConfiguration.class, RecordingAdminConfiguration.class)
			.run((context) -> {
				RecordingTopicAdmin admin = context.getBean(RecordingTopicAdmin.class);

				assertThat(admin.createdTopics()).containsExactly("computer", "order");
			});
	}

	/**
	 * 两个实体类解析到同一个主题名时只建一次,避免重复建主题请求.
	 */
	@Test
	void deDuplicatesTopicsWithTheSameName() {
		this.contextRunner.withUserConfiguration(SameTopicConfiguration.class, RecordingAdminConfiguration.class)
			.withPropertyValues("butterfly.rocketmq.entities.order.topic=computer")
			.run((context) -> assertThat(context.getBean(RecordingTopicAdmin.class).createdTopics())
				.containsExactly("computer"));
	}

	/**
	 * 关掉建主题开关后不再调用主题管理器,便于把建主题完全交给运维平台.
	 */
	@Test
	void createTopicDisabledSkipsTheAdmin() {
		this.contextRunner.withUserConfiguration(SampleConfiguration.class, RecordingAdminConfiguration.class)
			.withPropertyValues("butterfly.rocketmq.create-topics=false")
			.run((context) -> assertThat(context.getBean(RecordingTopicAdmin.class).createdTopics()).isEmpty());
	}

	/**
	 * 死信主题与重试主题是 Broker 的系统主题,绝不能交给主题管理器去建:建了会被 Broker 拒绝.
	 */
	@Test
	void neverCreatesDeadLetterOrRetryTopics() {
		this.contextRunner.withUserConfiguration(SampleConfiguration.class, RecordingAdminConfiguration.class)
			.run((context) -> assertThat(context.getBean(RecordingTopicAdmin.class).createdTopics())
				.noneMatch((topic) -> topic.startsWith("%DLQ%") || topic.startsWith("%RETRY%")));
	}

	/**
	 * 容器里连模板都没有时不应悄悄放过:取 Bean 时就要报错,并指明缺的是哪个 Bean.
	 */
	@Test
	void missingTemplateFailsFast() {
		new ApplicationContextRunner().withUserConfiguration(SampleConfiguration.class)
			.run((context) -> assertThatThrownBy(() -> context.getBean("computerRocketMqSender"))
				.hasRootCauseInstanceOf(IllegalStateException.class)
				.hasStackTraceContaining("RocketMQClientTemplate"));
	}

	/**
	 * 使用方自定义的同名 Bean 定义优先:同一配置类里显式声明的 {@code @Bean} 先于注册器登记,注册器发现已存在时跳过.
	 */
	@Test
	void userDeclaredSenderWins() {
		this.contextRunner.withUserConfiguration(CustomSenderConfiguration.class)
			.run((context) -> assertThat(context.getBean("computerRocketMqSender"))
				.isSameAs(CustomSenderConfiguration.CUSTOM));
	}

	/**
	 * {@code value} 为空数组时不注册任何 Bean.
	 */
	@Test
	void noEntityClassesRegistersNothing() {
		this.contextRunner.withUserConfiguration(EmptyConfiguration.class)
			.run((context) -> assertThat(context).doesNotHaveBean("computerRocketMqTopic")
				.doesNotHaveBean("computerRocketMqSender"));
	}

	/**
	 * 非法配置要在取用 Bean 时尽早暴露,而不是等到运行期才发现队列数是 0.
	 */
	@Test
	void invalidQueueCountFailsFast() {
		this.contextRunner.withUserConfiguration(SampleConfiguration.class)
			.withPropertyValues("butterfly.rocketmq.queue-count=0")
			.run((context) -> assertThatThrownBy(() -> context.getBean("computerRocketMqTopic"))
				.hasRootCauseInstanceOf(IllegalStateException.class)
				.hasStackTraceContaining("queue-count"));
	}

	@Configuration(proxyBeanMethods = false)
	@EnableRocketMqTemplates({ Computer.class, Order.class })
	static class SampleConfiguration {

	}

	@Configuration(proxyBeanMethods = false)
	@EnableRocketMqTemplates
	static class EmptyConfiguration {

	}

	@Configuration(proxyBeanMethods = false)
	@EnableRocketMqTemplates({ Computer.class, Order.class })
	static class SameTopicConfiguration {

	}

	/**
	 * 只需要一个模板 Bean:发送器构造时不连接任何 Broker.
	 */
	@Configuration(proxyBeanMethods = false)
	static class TemplateConfiguration {

		@Bean
		RocketMQClientTemplate rocketMQClientTemplate() {
			return new RocketMQClientTemplate();
		}

	}

	/**
	 * 记录被创建的主题,用来断言启动时的建主题动作.
	 */
	@Configuration(proxyBeanMethods = false)
	static class RecordingAdminConfiguration {

		@Bean
		RecordingTopicAdmin recordingTopicAdmin() {
			return new RecordingTopicAdmin();
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableRocketMqTemplates(Computer.class)
	static class CustomSenderConfiguration {

		static final RocketMqSender CUSTOM = new RocketMqSender(new RocketMQClientTemplate(),
				new RocketMqProperties().resolve(Computer.class));

		@Bean
		RocketMqSender computerRocketMqSender() {
			return CUSTOM;
		}

	}

	/**
	 * 只记名字、不做任何网络调用的主题管理器.
	 */
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

	static class Computer {

	}

	static class Order {

	}

}
