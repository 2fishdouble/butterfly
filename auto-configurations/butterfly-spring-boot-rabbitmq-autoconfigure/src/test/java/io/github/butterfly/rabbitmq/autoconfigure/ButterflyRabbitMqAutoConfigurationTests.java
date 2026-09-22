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

package io.github.butterfly.rabbitmq.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.config.ContainerCustomizer;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.CorrelationDataPostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 校验 {@link ButterflyRabbitMqAutoConfiguration}:唯一一个 JSON 消息转换器、唯一一个"发送 ID"处理器、默认监听
 * 容器的手动确认模式,以及三者都能被使用方自定义。全程不连 Broker。
 */
class ButterflyRabbitMqAutoConfigurationTests {

	/**
	 * 同时装配 Boot 的 Rabbit 自动配置:这些 Bean 本来就要和 Boot 默认创建的模板、默认监听容器工厂协作,自己配的
	 * {@link RabbitMqProperties} 也必须来自它。
	 */
	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withConfiguration(
			AutoConfigurations.of(RabbitAutoConfiguration.class, ButterflyRabbitMqAutoConfiguration.class));

	/**
	 * Boot 只在容器中恰好存在一个 {@link MessageConverter} 时采用它,所以这里必须是"有且仅有一个".
	 */
	@Test
	void registersExactlyOneJsonMessageConverter() {
		this.contextRunner.run((context) -> {
			assertThat(context).hasSingleBean(MessageConverter.class);
			assertThat(context.getBean(MessageConverter.class)).isInstanceOf(JacksonJsonMessageConverter.class);
		});
	}

	/**
	 * 使用方自定义转换器后,本配置必须退让,否则会破坏"唯一一个转换器"的前提,Boot 反倒两个都不用.
	 */
	@Test
	void backsOffWhenUserDefinesMessageConverter() {
		this.contextRunner.withUserConfiguration(CustomConverterConfiguration.class).run((context) -> {
			assertThat(context).hasSingleBean(MessageConverter.class);
			assertThat(context.getBean(MessageConverter.class)).isSameAs(CustomConverterConfiguration.CONVERTER);
		});
	}

	/**
	 * 传进来的 {@link CorrelationData} 必须原样返回:它有使用方持有的 future,替换掉就再也收不到 confirm.
	 */
	@Test
	void correlationIdPostProcessorKeepsCallerCorrelationDataAndStampsItsId() {
		this.contextRunner.run((context) -> {
			CorrelationDataPostProcessor processor = context.getBean(CorrelationDataPostProcessor.class);
			CorrelationData correlationData = new CorrelationData("order-1");
			Message message = new Message(new byte[0], new MessageProperties());

			CorrelationData result = processor.postProcess(message, correlationData);

			assertThat(result).isSameAs(correlationData);
			assertThat(message.getMessageProperties().getCorrelationId()).isEqualTo("order-1");
		});
	}

	/**
	 * 使用方没传 {@link CorrelationData} 时(Spring AMQP 会以 {@code null} 调用处理器)也要现造一个,并把它盖到消息
	 * 属性上,否则"每条消息都有 ID"就不成立.
	 */
	@Test
	void correlationIdPostProcessorGeneratesIdWhenCallerPassesNone() {
		this.contextRunner.run((context) -> {
			CorrelationDataPostProcessor processor = context.getBean(CorrelationDataPostProcessor.class);
			Message message = new Message(new byte[0], new MessageProperties());

			CorrelationData result = processor.postProcess(message, null);

			assertThat(result.getId()).isNotBlank();
			assertThat(message.getMessageProperties().getCorrelationId()).isEqualTo(result.getId());
		});
	}

	/**
	 * 处理器必须真的被装到模板上:Spring AMQP 只提供 setter,Boot 不会自己去容器里找这个类型的 Bean.
	 */
	@Test
	void appliesCorrelationIdPostProcessorToRabbitTemplate() {
		this.contextRunner.run((context) -> {
			RabbitTemplate template = new RabbitTemplate();

			context.getBean(RabbitTemplateCustomizer.class).customize(template);

			assertThat(ReflectionTestUtils.getField(template, "correlationDataPostProcessor"))
				.isSameAs(context.getBean(CorrelationDataPostProcessor.class));
		});
	}

	/**
	 * 端到端一次:Boot 自动创建的那个模板上应当已经装着处理器(全程不连 Broker).
	 */
	@Test
	void bootAutoConfiguredTemplateGetsCorrelationIdPostProcessor() {
		this.contextRunner.run((context) -> assertThat(
				ReflectionTestUtils.getField(context.getBean(RabbitTemplate.class), "correlationDataPostProcessor"))
			.isSameAs(context.getBean(CorrelationDataPostProcessor.class)));
	}

	/**
	 * 使用方自定义处理器后本配置退让,但仍要把它(而不是本配置的那个)装到模板上.
	 */
	@Test
	void backsOffWhenUserDefinesCorrelationIdPostProcessorButStillAppliesIt() {
		this.contextRunner.withUserConfiguration(CustomCorrelationDataConfiguration.class).run((context) -> {
			assertThat(context).hasSingleBean(CorrelationDataPostProcessor.class);
			assertThat(context.getBean(CorrelationDataPostProcessor.class))
				.isSameAs(CustomCorrelationDataConfiguration.PROCESSOR);

			RabbitTemplate template = new RabbitTemplate();
			context.getBean(RabbitTemplateCustomizer.class).customize(template);
			assertThat(ReflectionTestUtils.getField(template, "correlationDataPostProcessor"))
				.isSameAs(CustomCorrelationDataConfiguration.PROCESSOR);
		});
	}

	/**
	 * Boot 默认监听容器工厂建出来的容器必须是手动确认 —— 先确认定制器真的被 Boot 装进了那个工厂,再确认它改的是容器.
	 */
	@Test
	void bootDefaultListenerContainerFactoryCreatesManualAckContainers() {
		this.contextRunner.run((context) -> {
			SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();

			containerCustomizer(context).configure(container);

			assertThat(container.getAcknowledgeMode()).isEqualTo(AcknowledgeMode.MANUAL);
		});
	}

	/**
	 * 使用方显式配了 {@code spring.rabbitmq.listener.simple.acknowledge-mode} 就不覆盖,与 Boot
	 * 的一贯约定一致.
	 */
	@Test
	void honoursExplicitBootAcknowledgeMode() {
		this.contextRunner.withPropertyValues("spring.rabbitmq.listener.simple.acknowledge-mode=auto")
			.run((context) -> {
				SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();

				containerCustomizer(context).configure(container);

				assertThat(container.getAcknowledgeMode()).isEqualTo(AcknowledgeMode.AUTO);
			});
	}

	/**
	 * Boot 只用唯一一个 {@link ContainerCustomizer},所以使用方自定义时本配置必须退让,让那个 Bean 被采用.
	 */
	@Test
	void backsOffWhenUserDefinesContainerCustomizer() {
		this.contextRunner.withUserConfiguration(CustomContainerCustomizerConfiguration.class).run((context) -> {
			assertThat(context).doesNotHaveBean("butterflyRabbitManualAckContainerCustomizer");

			SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
			containerCustomizer(context).configure(container);

			assertThat(container.getAcknowledgeMode()).isEqualTo(AcknowledgeMode.NONE);
		});
	}

	@SuppressWarnings("unchecked")
	private ContainerCustomizer<SimpleMessageListenerContainer> containerCustomizer(ApplicationContext context) {
		SimpleRabbitListenerContainerFactory factory = context.getBean("rabbitListenerContainerFactory",
				SimpleRabbitListenerContainerFactory.class);
		ContainerCustomizer<SimpleMessageListenerContainer> customizer = (ContainerCustomizer<SimpleMessageListenerContainer>) ReflectionTestUtils
			.getField(factory, "containerCustomizer");
		assertThat(customizer).isNotNull();
		return customizer;
	}

	@Configuration(proxyBeanMethods = false)
	static class CustomConverterConfiguration {

		static final MessageConverter CONVERTER = new JacksonJsonMessageConverter();

		@Bean
		MessageConverter customMessageConverter() {
			return CONVERTER;
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class CustomCorrelationDataConfiguration {

		/**
		 * 使用方自定义处理器:沿用调用方给的关联数据,缺省时现造一个.
		 * <p>
		 * {@code postProcess} 的入参可空、返回值非空,因此不能把入参直接透传回去.
		 */
		static final CorrelationDataPostProcessor PROCESSOR = (message, correlationData) -> (correlationData != null)
				? correlationData : new CorrelationData();

		@Bean
		CorrelationDataPostProcessor customCorrelationDataPostProcessor() {
			return PROCESSOR;
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class CustomContainerCustomizerConfiguration {

		@Bean
		ContainerCustomizer<SimpleMessageListenerContainer> customContainerCustomizer() {
			// 用一个绝不会出现在别处的取值,以便区分"被应用"与"没有定制器"
			return (container) -> container.setAcknowledgeMode(AcknowledgeMode.NONE);
		}

	}

}
