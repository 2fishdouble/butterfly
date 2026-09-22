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

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.backoff.ExponentialBackOff;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 校验 {@link EnableRabbitMqTemplates} 按实体类声明拓扑与监听容器工厂:命名规则、默认值、死信与延时拓扑、
 * 重试通知链的退避参数与"重试耗尽后拒绝原投递",以及本模块不注册任何 {@code RabbitTemplate}。全程不触网。
 */
class EnableRabbitMqTemplatesTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(RabbitInfrastructureConfiguration.class);

	/**
	 * 每个实体类都要拿到一份拓扑声明,默认包含主交换机/队列/绑定与死信交换机/队列/绑定.
	 */
	@Test
	void registersDeclarablesForEveryEntityClass() {
		this.contextRunner.withUserConfiguration(SampleConfiguration.class).run((context) -> {
			assertThat(context).hasBean("computerDeclarables").hasBean("orderDeclarables");

			Declarables computer = declarables(context, "computerDeclarables");

			assertThat(computer.getDeclarables()).hasSize(6);
			assertThat(computer.getDeclarablesByType(Exchange.class)).extracting(Exchange::getName)
				.containsExactly("computer", "computer.dlx");
			assertThat(computer.getDeclarablesByType(Queue.class)).extracting(Queue::getName)
				.containsExactly("computer", "computer.dlq");
			assertThat(bindings(computer)).containsExactly("computer->computer->computer",
					"computer.dlx->computer.dead->computer.dlq");
		});
	}

	/**
	 * 主队列必须是持久化的,并带上指向死信交换机与死信路由键的 {@code x-dead-letter-*} 参数;交换机默认是主题型.
	 */
	@Test
	void mainQueueCarriesDeadLetterArguments() {
		this.contextRunner.withUserConfiguration(SampleConfiguration.class).run((context) -> {
			Declarables computer = declarables(context, "computerDeclarables");

			Queue queue = computer.getDeclarablesByType(Queue.class).getFirst();
			assertThat(queue.isDurable()).isTrue();
			assertThat(queue.isExclusive()).isFalse();
			assertThat(queue.isAutoDelete()).isFalse();
			assertThat(queue.getArguments()).containsEntry("x-dead-letter-exchange", "computer.dlx")
				.containsEntry("x-dead-letter-routing-key", "computer.dead");

			assertThat(computer.getDeclarablesByType(Exchange.class).getFirst().getType()).isEqualTo("topic");
			assertThat(computer.getDeclarablesByType(Exchange.class).getFirst().isDurable()).isTrue();
		});
	}

	/**
	 * 名称、交换机类型与持久化都要来自配置,且改名后派生出的死信名称要跟着改.
	 */
	@Test
	void configuredNamesAndExchangeTypeAreUsed() {
		this.contextRunner.withUserConfiguration(SampleConfiguration.class)
			.withPropertyValues("butterfly.rabbitmq.exchange-type=direct", "butterfly.rabbitmq.durable=false",
					"butterfly.rabbitmq.auto-delete=true",
					"butterfly.rabbitmq.entities.computer.exchange=butterfly-computer",
					"butterfly.rabbitmq.entities.computer.queue=butterfly-computer-queue",
					"butterfly.rabbitmq.entities.computer.routing-key=computer.created")
			.run((context) -> {
				Declarables computer = declarables(context, "computerDeclarables");

				assertThat(computer.getDeclarablesByType(Exchange.class)).extracting(Exchange::getName)
					.containsExactly("butterfly-computer", "butterfly-computer.dlx");
				assertThat(computer.getDeclarablesByType(Queue.class)).extracting(Queue::getName)
					.containsExactly("butterfly-computer-queue", "butterfly-computer-queue.dlq");
				assertThat(bindings(computer)).containsExactly(
						"butterfly-computer->computer.created->butterfly-computer-queue",
						"butterfly-computer.dlx->computer.created.dead->butterfly-computer-queue.dlq");

				for (Exchange exchange : computer.getDeclarablesByType(Exchange.class)) {
					assertThat(exchange.getType()).isEqualTo("direct");
					assertThat(exchange.isDurable()).isFalse();
					assertThat(exchange.isAutoDelete()).isTrue();
				}

				// 未覆盖的实体类仍沿用全局默认值
				assertThat(declarables(context, "orderDeclarables").getDeclarablesByType(Exchange.class))
					.extracting(Exchange::getName)
					.containsExactly("order", "order.dlx");
			});
	}

	/**
	 * 扇形交换机忽略路由键,绑定的路由键必须为空而不是沿用配置里的主路由键.
	 */
	@Test
	void fanoutExchangeBindingCarriesNoRoutingKey() {
		this.contextRunner.withUserConfiguration(SampleConfiguration.class)
			.withPropertyValues("butterfly.rabbitmq.entities.computer.exchange-type=fanout")
			.run((context) -> {
				Declarables computer = declarables(context, "computerDeclarables");

				assertThat(computer.getDeclarablesByType(Exchange.class).getFirst().getType()).isEqualTo("fanout");
				assertThat(bindings(computer)).containsExactly("computer->->computer", "computer.dlx->->computer.dlq");
			});
	}

	/**
	 * 关掉死信后只声明主拓扑:主队列不再挂 {@code x-dead-letter-*},重试耗尽时消息会被 Broker 丢弃,但投递本身仍会被 拒绝结算(见
	 * {@link #retryExhaustionStillRejectsWhenDeadLetterIsDisabled()})。
	 * <p>
	 * 死信改走 Broker 的 dead-letter 之后,整条链路都不再需要 {@code AmqpTemplate}。
	 */
	@Test
	void deadLetterDisabledDeclaresMainTopologyOnlyAndNeedsNoTemplate() {
		new ApplicationContextRunner()
			.withUserConfiguration(ConnectionFactoryConfiguration.class, SampleConfiguration.class)
			.withPropertyValues("butterfly.rabbitmq.dead-letter.enabled=false")
			.run((context) -> {
				Declarables computer = declarables(context, "computerDeclarables");

				assertThat(computer.getDeclarables()).hasSize(3);
				assertThat(computer.getDeclarablesByType(Exchange.class)).extracting(Exchange::getName)
					.containsExactly("computer");
				assertThat(computer.getDeclarablesByType(Queue.class)).extracting(Queue::getName)
					.containsExactly("computer");
				assertThat(computer.getDeclarablesByType(Queue.class).getFirst().getArguments())
					.doesNotContainKey("x-dead-letter-exchange");
				assertThat(bindings(computer)).containsExactly("computer->computer->computer");

				assertThat(containerFactory(context, "computerRabbitListenerContainerFactory")).isNotNull();
				assertThat(context.getBeansOfType(AmqpTemplate.class)).isEmpty();
			});
	}

	/**
	 * 打开延时后追加一组延时拓扑,延时队列以 TTL + 死信参数在到期后把消息回投主交换机与主路由键.
	 */
	@Test
	void delayTopologyDeadLettersBackToMainExchange() {
		this.contextRunner.withUserConfiguration(SampleConfiguration.class)
			.withPropertyValues("butterfly.rabbitmq.entities.computer.delay.enabled=true",
					"butterfly.rabbitmq.entities.computer.delay.ttl=1500ms")
			.run((context) -> {
				Declarables computer = declarables(context, "computerDeclarables");

				assertThat(computer.getDeclarables()).hasSize(9);
				assertThat(computer.getDeclarablesByType(Exchange.class)).extracting(Exchange::getName)
					.containsExactly("computer", "computer.dlx", "computer.delay");
				assertThat(computer.getDeclarablesByType(Queue.class)).extracting(Queue::getName)
					.containsExactly("computer", "computer.dlq", "computer.delay");
				assertThat(bindings(computer)).containsExactly("computer->computer->computer",
						"computer.dlx->computer.dead->computer.dlq", "computer.delay->computer.delay->computer.delay");

				Queue delayQueue = computer.getDeclarablesByType(Queue.class).get(2);
				assertThat(delayQueue.getArguments()).containsEntry("x-message-ttl", 1500)
					.containsEntry("x-dead-letter-exchange", "computer")
					.containsEntry("x-dead-letter-routing-key", "computer");
			});
	}

	/**
	 * 每个实体类注册一个监听容器工厂:取容器中唯一的连接工厂与消息转换器,并按配置设置并发、预取、确认模式与失败处理.
	 * <p>
	 * 同时确认本模块不注册 {@code RabbitTemplate}(也不按实体类复制).
	 */
	@Test
	void registersListenerContainerFactoryPerEntityUsingSharedInfrastructure() {
		this.contextRunner.withUserConfiguration(SampleConfiguration.class, ConverterConfiguration.class)
			.run((context) -> {
				assertThat(context).hasBean("computerRabbitListenerContainerFactory")
					.hasBean("orderRabbitListenerContainerFactory");

				SimpleRabbitListenerContainerFactory factory = containerFactory(context,
						"computerRabbitListenerContainerFactory");
				assertThat(ReflectionTestUtils.getField(factory, "connectionFactory"))
					.isSameAs(context.getBean(ConnectionFactory.class));
				assertThat(ReflectionTestUtils.getField(factory, "messageConverter"))
					.isSameAs(context.getBean(MessageConverter.class));
				assertThat(ReflectionTestUtils.getField(factory, "concurrentConsumers")).isEqualTo(1);
				assertThat(ReflectionTestUtils.getField(factory, "maxConcurrentConsumers")).isEqualTo(1);
				assertThat(ReflectionTestUtils.getField(factory, "prefetchCount")).isEqualTo(1);
				assertThat(ReflectionTestUtils.getField(factory, "defaultRequeueRejected")).isEqualTo(false);
				assertThat(ReflectionTestUtils.getField(factory, "acknowledgeMode")).isEqualTo(AcknowledgeMode.MANUAL);

				assertThat(context).doesNotHaveBean("computerRabbitTemplate").doesNotHaveBean("orderRabbitTemplate");
				assertThat(context.getBeansOfType(RabbitTemplate.class)).as("本模块不注册 RabbitTemplate").isEmpty();
			});
	}

	/**
	 * 实体类级监听配置要覆盖全局监听配置.
	 */
	@Test
	void listenerConfigurationOverridesAreApplied() {
		this.contextRunner.withUserConfiguration(SampleConfiguration.class)
			.withPropertyValues("butterfly.rabbitmq.entities.computer.listener.concurrency=5",
					"butterfly.rabbitmq.entities.computer.listener.prefetch=25",
					"butterfly.rabbitmq.entities.computer.listener.requeue-rejected=true",
					"butterfly.rabbitmq.entities.computer.listener.acknowledge-mode=auto")
			.run((context) -> {
				SimpleRabbitListenerContainerFactory factory = containerFactory(context,
						"computerRabbitListenerContainerFactory");

				assertThat(ReflectionTestUtils.getField(factory, "concurrentConsumers")).isEqualTo(5);
				assertThat(ReflectionTestUtils.getField(factory, "maxConcurrentConsumers")).isEqualTo(5);
				assertThat(ReflectionTestUtils.getField(factory, "prefetchCount")).isEqualTo(25);
				assertThat(ReflectionTestUtils.getField(factory, "defaultRequeueRejected")).isEqualTo(true);
				assertThat(ReflectionTestUtils.getField(factory, "acknowledgeMode")).isEqualTo(AcknowledgeMode.AUTO);
			});
	}

	/**
	 * 重试通知链的退避参数必须来自配置;{@code maxAttempts} 是首次之后的次数,因此配置的总投递次数要减一.
	 */
	@Test
	void retryAdviceFollowsConfiguredBackOff() {
		this.contextRunner.withUserConfiguration(SampleConfiguration.class)
			.withPropertyValues("butterfly.rabbitmq.retry.attempts=4", "butterfly.rabbitmq.retry.delay=500ms",
					"butterfly.rabbitmq.retry.multiplier=3.0", "butterfly.rabbitmq.retry.max-delay=10s")
			.run((context) -> {
				ExponentialBackOff backOff = backOff(context, "computerRabbitListenerContainerFactory");

				assertThat(backOff.getMaxAttempts()).isEqualTo(3L);
				assertThat(backOff.getInitialInterval()).isEqualTo(500L);
				assertThat(backOff.getMultiplier()).isEqualTo(3.0);
				assertThat(backOff.getMaxInterval()).isEqualTo(10_000L);
			});
	}

	/**
	 * 默认退避为 1s 起、倍率 2.0、上限 30s,总投递 3 次.
	 */
	@Test
	void retryAdviceFallsBackToBuiltInDefaults() {
		this.contextRunner.withUserConfiguration(SampleConfiguration.class).run((context) -> {
			ExponentialBackOff backOff = backOff(context, "computerRabbitListenerContainerFactory");

			assertThat(backOff.getMaxAttempts()).isEqualTo(2L);
			assertThat(backOff.getInitialInterval()).isEqualTo(1000L);
			assertThat(backOff.getMultiplier()).isEqualTo(2.0);
			assertThat(backOff.getMaxInterval()).isEqualTo(30_000L);
		});
	}

	/**
	 * 重试耗尽后必须<b>拒绝</b>原投递:只有抛出让容器强制 {@code requeue=false} 的
	 * {@code AmqpRejectAndDontRequeueException},Broker 才会按主队列的 {@code x-dead-letter-*} 把它
	 * dead-letter 到死信队列, 同时这次投递才算结算掉.
	 * <p>
	 * 这是对「republish 之后原消息既没 ack 也没 nack、一直停在 unacked」那个缺陷的回归测试:通知链必须把异常抛出去,
	 * 而不是自己投一份就正常返回。
	 */
	@Test
	void retryExhaustionRejectsMessageSoBrokerDeadLettersIt() {
		this.contextRunner.withUserConfiguration(SampleConfiguration.class)
			.withPropertyValues("butterfly.rabbitmq.retry.attempts=3", "butterfly.rabbitmq.retry.delay=1ms",
					"butterfly.rabbitmq.retry.multiplier=1.0", "butterfly.rabbitmq.retry.max-delay=1ms")
			.run((context) -> {
				FailingMethodInvocation invocation = failingInvocation();

				assertThatThrownBy(
						() -> retryAdvice(context, "computerRabbitListenerContainerFactory").invoke(invocation))
					.isInstanceOfSatisfying(AmqpRejectAndDontRequeueException.class, (reject) -> {
						assertThat(reject.isRejectManual()).as("MANUAL 模式下容器只有看到 rejectManual=true 才会拒绝").isTrue();
						assertThat(reject).hasRootCauseInstanceOf(IllegalStateException.class);
					});

				assertThat(invocation.attempts).isEqualTo(3);
			});
	}

	/**
	 * {@code attempts=1} 表示不重试:只投递一次,随后同样要拒绝原投递进入死信链路.
	 */
	@Test
	void singleAttemptSkipsRetriesAndStillRejects() {
		this.contextRunner.withUserConfiguration(SampleConfiguration.class)
			.withPropertyValues("butterfly.rabbitmq.retry.attempts=1")
			.run((context) -> {
				FailingMethodInvocation invocation = failingInvocation();

				assertThatThrownBy(
						() -> retryAdvice(context, "computerRabbitListenerContainerFactory").invoke(invocation))
					.isInstanceOfSatisfying(AmqpRejectAndDontRequeueException.class,
							(reject) -> assertThat(reject.isRejectManual()).isTrue());

				assertThat(invocation.attempts).isEqualTo(1);
			});
	}

	/**
	 * 关掉死信时一样要拒绝原投递:主队列上没有 {@code x-dead-letter-*},消息会被 Broker 丢弃,但绝不能留在 unacked
	 * 等通道关闭后重投。
	 */
	@Test
	void retryExhaustionStillRejectsWhenDeadLetterIsDisabled() {
		this.contextRunner.withUserConfiguration(SampleConfiguration.class)
			.withPropertyValues("butterfly.rabbitmq.dead-letter.enabled=false", "butterfly.rabbitmq.retry.attempts=2",
					"butterfly.rabbitmq.retry.delay=1ms")
			.run((context) -> {
				FailingMethodInvocation invocation = failingInvocation();

				assertThatThrownBy(
						() -> retryAdvice(context, "computerRabbitListenerContainerFactory").invoke(invocation))
					.isInstanceOfSatisfying(AmqpRejectAndDontRequeueException.class,
							(reject) -> assertThat(reject.isRejectManual()).isTrue());

				assertThat(invocation.attempts).isEqualTo(2);
			});
	}

	/**
	 * 构造一个"每次都失败"的调用:通知链按参数列表的第 2 个元素取消息(第 1 个是 Channel).
	 * @return 必然失败的调用
	 */
	private static FailingMethodInvocation failingInvocation() {
		Message message = MessageBuilder.withBody("boom".getBytes(StandardCharsets.UTF_8)).build();
		// 元素类型显式声明为可空:数组字面量在 @NullMarked 下默认是非空元素
		@Nullable Object[] arguments = { null, message };
		return new FailingMethodInvocation(arguments, new IllegalStateException("boom"));
	}

	/**
	 * 使用方自定义的同名 Bean 定义优先:同一配置类里显式声明的 {@code @Bean} 先于注册器登记,注册器发现已存在时跳过.
	 */
	@Test
	void userDeclaredDeclarablesWins() {
		this.contextRunner.withUserConfiguration(CustomDeclarablesConfiguration.class)
			.run((context) -> assertThat(context.getBean("computerDeclarables"))
				.isSameAs(CustomDeclarablesConfiguration.CUSTOM));
	}

	/**
	 * {@code value} 为空数组时不注册任何 Bean.
	 */
	@Test
	void noEntityClassesRegistersNothing() {
		this.contextRunner.withUserConfiguration(EmptyConfiguration.class)
			.run((context) -> assertThat(context).doesNotHaveBean("computerDeclarables")
				.doesNotHaveBean("computerRabbitListenerContainerFactory"));
	}

	/**
	 * 非法配置要在取用 Bean 时尽早暴露,而不是等到运行期才发现重试次数是 0.
	 */
	@Test
	void invalidAttemptsFailsFast() {
		this.contextRunner.withUserConfiguration(SampleConfiguration.class)
			.withPropertyValues("butterfly.rabbitmq.retry.attempts=0")
			.run((context) -> assertThatThrownBy(() -> context.getBean("computerDeclarables"))
				.hasRootCauseInstanceOf(IllegalStateException.class)
				.hasStackTraceContaining("attempts"));
	}

	private static Declarables declarables(ApplicationContext context, String beanName) {
		return context.getBean(beanName, Declarables.class);
	}

	private static SimpleRabbitListenerContainerFactory containerFactory(ApplicationContext context, String beanName) {
		return context.getBean(beanName, SimpleRabbitListenerContainerFactory.class);
	}

	private static MethodInterceptor retryAdvice(ApplicationContext context, String beanName) {
		Advice[] adviceChain = containerFactory(context, beanName).getAdviceChain();
		assertThat(adviceChain).hasSize(1);
		return (MethodInterceptor) adviceChain[0];
	}

	private static ExponentialBackOff backOff(ApplicationContext context, String beanName) {
		RetryTemplate retryTemplate = Objects.requireNonNull(
				(RetryTemplate) ReflectionTestUtils.getField(retryAdvice(context, beanName), "retryOperations"),
				"retryAdvice must hold a RetryTemplate");
		RetryPolicy retryPolicy = retryTemplate.getRetryPolicy();
		return (ExponentialBackOff) retryPolicy.getBackOff();
	}

	private static List<String> bindings(Declarables declarables) {
		return declarables.getDeclarablesByType(Binding.class)
			.stream()
			.map((binding) -> binding.getExchange() + "->" + binding.getRoutingKey() + "->" + binding.getDestination())
			.toList();
	}

	@Configuration(proxyBeanMethods = false)
	@EnableRabbitMqTemplates({ Computer.class, Order.class })
	static class SampleConfiguration {

	}

	@Configuration(proxyBeanMethods = false)
	@EnableRabbitMqTemplates
	static class EmptyConfiguration {

	}

	@Configuration(proxyBeanMethods = false)
	static class ConnectionFactoryConfiguration {

		@Bean
		ConnectionFactory connectionFactory() {
			return new CachingConnectionFactory();
		}

	}

	/**
	 * 只需要一个连接工厂:死信投递走 Broker 的 dead-letter,不再需要 {@code RabbitTemplate}.
	 */
	@Configuration(proxyBeanMethods = false)
	static class RabbitInfrastructureConfiguration {

		@Bean
		ConnectionFactory connectionFactory() {
			return new CachingConnectionFactory();
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class ConverterConfiguration {

		@Bean
		MessageConverter messageConverter() {
			return new JacksonJsonMessageConverter();
		}

	}

	@Configuration(proxyBeanMethods = false)
	@EnableRabbitMqTemplates(Computer.class)
	static class CustomDeclarablesConfiguration {

		static final Declarables CUSTOM = new Declarables(new Queue("computer-from-user"));

		@Bean
		Declarables computerDeclarables() {
			return CUSTOM;
		}

	}

	/**
	 * 必然失败的调用:记录被调用次数后抛异常.
	 */
	static class FailingMethodInvocation implements MethodInvocation {

		private final @Nullable Object[] arguments;

		private final RuntimeException failure;

		private int attempts;

		FailingMethodInvocation(@Nullable Object[] arguments, RuntimeException failure) {
			this.arguments = arguments;
			this.failure = failure;
		}

		@Override
		public Object proceed() {
			this.attempts++;
			throw this.failure;
		}

		@Override
		public @Nullable Object[] getArguments() {
			return this.arguments;
		}

		@Override
		public Object getThis() {
			throw new UnsupportedOperationException();
		}

		@Override
		public AccessibleObject getStaticPart() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Method getMethod() {
			throw new UnsupportedOperationException();
		}

	}

	static class Computer {

	}

	static class Order {

	}

}
