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

import org.aopalliance.intercept.MethodInterceptor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link EnableRabbitMqTemplates} 的导入注册器:按实体类声明 RabbitMQ 拓扑与带重试的监听容器工厂.
 * <p>
 * 对 {@link EnableRabbitMqTemplates#value()} 中的每个实体类注册两个 Bean:
 * <ul>
 * <li>{@code <实体>Declarables}:主交换机、主队列与绑定;开启死信时追加死信交换机、死信队列与绑定,并把 {@code x-dead-letter-*}
 * 挂到主队列上;开启延时时追加延时交换机、延时队列与绑定,延时队列以 {@code x-message-ttl} + {@code x-dead-letter-*}
 * 在到期后把消息回投主交换机。这些 Bean 由 Boot 注册的 {@code RabbitAdmin} 在启动时自动声明到 Broker;</li>
 * <li>{@code <实体>RabbitListenerContainerFactory}:预置重试通知链;重试耗尽后拒绝原投递,由 Broker 按主队列上的
 * {@code x-dead-letter-*} 把它 dead-letter 到死信队列。</li>
 * </ul>
 * <p>
 * 本注册器<b>不</b>注册 {@code RabbitTemplate}:收发统一使用容器中 Boot 自动配置的那一个模板,避免按实体类复制出
 * 多份连接与转换配置;各监听容器工厂复用容器中唯一的 {@link MessageConverter},从而与模板的序列化方式保持一致。
 * <p>
 * 死信投递走的是 Broker 的 dead-letter 机制,不需要 {@code AmqpTemplate},因此死信链路对模板的有无与数量没有要求。
 * <p>
 * 任一同名 Bean 已存在时跳过该实体类的对应注册,不覆盖使用方定义。
 * <p>
 * 同时实现 {@link BeanFactoryAware} 持有 {@link BeanFactory},以便在实例供应器里按类型取出连接工厂与
 * {@link MessageConverter}。
 * <p>
 * 本类的可空性只出现在字段、方法参数与方法返回值上,方法体内不声明可空的局部变量。
 */
public class RabbitMqRegistrar implements ImportBeanDefinitionRegistrar, BeanFactoryAware {

	private static final String DECLARABLES_BEAN_NAME_SUFFIX = "Declarables";

	private static final String LISTENER_FACTORY_BEAN_NAME_SUFFIX = "RabbitListenerContainerFactory";

	private static final Log logger = LogFactory.getLog(RabbitMqRegistrar.class);

	private @Nullable BeanFactory beanFactory;

	/**
	 * 记录当前 {@link BeanFactory},供注册 Bean 定义时在实例供应器内按类型查找 {@link ConnectionFactory}.
	 * @param beanFactory spring 容器传入的 BeanFactory
	 * @throws BeansException spring 注入 BeanFactory 失败时抛出
	 */
	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
	}

	/**
	 * 读取标注 {@link EnableRabbitMqTemplates} 的类的注解属性,并为其中的每个实体类注册拓扑与监听容器工厂的 Bean 定义.
	 * <p>
	 * 取不到 {@link EnableRabbitMqTemplates} 的注解属性(结果为 {@code null})时不注册任何 Bean。
	 * @param importingClassMetadata 导入类(即标注 {@link EnableRabbitMqTemplates} 的类)的注解元数据
	 * @param registry spring 的 Bean 定义注册表
	 */
	@Override
	public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
		AnnotationAttributes attributes = AnnotationAttributes
			.fromMap(importingClassMetadata.getAnnotationAttributes(EnableRabbitMqTemplates.class.getName()));

		if (attributes == null) {
			return;
		}

		Class<?>[] entityClasses = attributes.getClassArray("value");

		for (Class<?> clazz : entityClasses) {
			registerDeclarables(registry, clazz);
			registerListenerContainerFactory(registry, clazz);
		}
	}

	/**
	 * 注册该实体类的主拓扑、死信拓扑与可选延时拓扑.
	 * @param registry spring 的 Bean 定义注册表
	 * @param entityClass {@link EnableRabbitMqTemplates} 中声明的实体类
	 */
	private void registerDeclarables(BeanDefinitionRegistry registry, Class<?> entityClass) {
		String beanName = beanName(entityClass, DECLARABLES_BEAN_NAME_SUFFIX);

		if (registry.containsBeanDefinition(beanName)) {
			return;
		}

		RootBeanDefinition beanDefinition = new RootBeanDefinition();
		beanDefinition.setBeanClass(Declarables.class);
		beanDefinition.setTargetType(Declarables.class);
		beanDefinition.setInstanceSupplier(() -> buildDeclarables(entityClass));

		registry.registerBeanDefinition(beanName, beanDefinition);
	}

	private Declarables buildDeclarables(Class<?> entityClass) {
		RabbitMqProperties.EntityDefinition definition = resolveProperties().resolve(entityClass);
		List<Declarable> declarables = new ArrayList<>();

		Exchange exchange = buildExchange(definition.exchange(), definition);
		Queue queue = buildMainQueue(definition);
		declarables.add(exchange);
		declarables.add(queue);
		declarables.add(bind(queue, exchange, definition.routingKey()));

		if (definition.deadLetterEnabled()) {
			Exchange deadLetterExchange = buildExchange(definition.deadLetterExchange(), definition);
			Queue deadLetterQueue = buildPlainQueue(definition.deadLetterQueue(), definition);
			declarables.add(deadLetterExchange);
			declarables.add(deadLetterQueue);
			declarables.add(bind(deadLetterQueue, deadLetterExchange, definition.deadLetterRoutingKey()));
		}

		if (definition.delayEnabled()) {
			Exchange delayExchange = buildExchange(definition.delayExchange(), definition);
			Queue delayQueue = queueBuilder(definition.delayQueue(), definition)
				.ttl(Math.toIntExact(definition.delayTtl().toMillis()))
				.deadLetterExchange(definition.exchange())
				.deadLetterRoutingKey(definition.routingKey())
				.build();
			declarables.add(delayExchange);
			declarables.add(delayQueue);
			declarables.add(bind(delayQueue, delayExchange, definition.delayRoutingKey()));
		}

		return new Declarables(declarables);
	}

	/**
	 * 构建主队列:持久化与自动删除取自配置,并在开启死信时挂上 {@code x-dead-letter-*} 参数.
	 * @param definition 解析后的拓扑定义
	 * @return 主队列
	 */
	private Queue buildMainQueue(RabbitMqProperties.EntityDefinition definition) {
		QueueBuilder builder = queueBuilder(definition.queue(), definition);
		if (definition.deadLetterEnabled()) {
			builder.deadLetterExchange(definition.deadLetterExchange())
				.deadLetterRoutingKey(definition.deadLetterRoutingKey());
		}
		return builder.build();
	}

	private Queue buildPlainQueue(String queueName, RabbitMqProperties.EntityDefinition definition) {
		return queueBuilder(queueName, definition).build();
	}

	private QueueBuilder queueBuilder(String queueName, RabbitMqProperties.EntityDefinition definition) {
		QueueBuilder builder = definition.durable() ? QueueBuilder.durable(queueName)
				: QueueBuilder.nonDurable(queueName);
		if (definition.autoDelete()) {
			builder.autoDelete();
		}
		return builder;
	}

	private Exchange buildExchange(String name, RabbitMqProperties.EntityDefinition definition) {
		ExchangeBuilder builder = switch (definition.exchangeType()) {
			case DIRECT -> ExchangeBuilder.directExchange(name);
			case TOPIC -> ExchangeBuilder.topicExchange(name);
			case FANOUT -> ExchangeBuilder.fanoutExchange(name);
		};
		builder.durable(definition.durable());
		if (definition.autoDelete()) {
			builder.autoDelete();
		}
		return builder.build();
	}

	/**
	 * 按交换机具体类型选择绑定方式:主题与直连交换机按路由键绑定,扇形交换机忽略路由键.
	 * @param queue 目标队列
	 * @param exchange 目标交换机
	 * @param routingKey 路由键
	 * @return 绑定
	 */
	private Binding bind(Queue queue, Exchange exchange, String routingKey) {
		return switch (exchange) {
			case TopicExchange topicExchange -> BindingBuilder.bind(queue).to(topicExchange).with(routingKey);
			case DirectExchange directExchange -> BindingBuilder.bind(queue).to(directExchange).with(routingKey);
			case FanoutExchange fanoutExchange -> BindingBuilder.bind(queue).to(fanoutExchange);
			default -> throw new IllegalStateException("Unsupported exchange type: " + exchange.getClass().getName());
		};
	}

	/**
	 * 注册该实体类专属的 {@link SimpleRabbitListenerContainerFactory}:预置重试通知链与死信投递.
	 * <p>
	 * 使用方在 {@code @RabbitListener} 上通过
	 * {@code containerFactory = "<实体>RabbitListenerContainerFactory"} 选用它,即可获得"重试 →
	 * 死信"链路。
	 * @param registry spring 的 Bean 定义注册表
	 * @param entityClass {@link EnableRabbitMqTemplates} 中声明的实体类
	 */
	private void registerListenerContainerFactory(BeanDefinitionRegistry registry, Class<?> entityClass) {
		String beanName = beanName(entityClass, LISTENER_FACTORY_BEAN_NAME_SUFFIX);

		if (registry.containsBeanDefinition(beanName)) {
			return;
		}

		RootBeanDefinition beanDefinition = new RootBeanDefinition();
		beanDefinition.setBeanClass(SimpleRabbitListenerContainerFactory.class);
		beanDefinition.setTargetType(SimpleRabbitListenerContainerFactory.class);
		beanDefinition.setInstanceSupplier(() -> buildListenerContainerFactory(entityClass));

		registry.registerBeanDefinition(beanName, beanDefinition);
	}

	private SimpleRabbitListenerContainerFactory buildListenerContainerFactory(Class<?> entityClass) {
		RabbitMqProperties.EntityDefinition definition = resolveProperties().resolve(entityClass);

		SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
		factory.setConnectionFactory(requireConnectionFactory());
		factory.setConcurrentConsumers(definition.concurrency());
		factory.setMaxConcurrentConsumers(definition.concurrency());
		factory.setPrefetchCount(definition.prefetch());
		factory.setDefaultRequeueRejected(definition.requeueRejected());
		factory.setAcknowledgeMode(definition.acknowledgeMode());
		MessageConverter messageConverter = resolveMessageConverter();
		if (messageConverter != null) {
			factory.setMessageConverter(messageConverter);
		}
		factory.setAdviceChain(buildRetryAdvice(definition));
		return factory;
	}

	/**
	 * 构建"重试 → 死信"通知链.
	 * <p>
	 * {@link RabbitMqProperties.EntityDefinition#attempts()} 是含首次投递的总次数,而
	 * {@link RetryInterceptorBuilder#maxRetries(int)} 只统计首次之后的次数,因此这里要减一。
	 * <p>
	 * 重试耗尽后由 {@link #rejectingMessageRecoverer()} 抛出 {@code rejectManual=true} 的
	 * {@code AmqpRejectAndDontRequeueException},容器随即 <b>拒绝</b>这次投递(强制
	 * {@code requeue=false},不受 {@code requeue-rejected} 影响),Broker 再按主队列上声明的
	 * {@code x-dead-letter-exchange} / {@code x-dead-letter-routing-key} 把它 dead-letter
	 * 到死信交换机,最终进入死信队列。
	 * <p>
	 * 这里刻意<b>不</b>用 {@code RepublishMessageRecoverer}:它工作的
	 * {@code MessageRecoverer#recover(Message,
	 * Throwable)} 签名里没有 Channel,既不能 ack 也不能 nack,原投递会一直停在 unacked,直到通道关闭被重投 —— 也就是
	 * 死信队列收到副本、主队列又把同一条再投一次。交给 Broker dead-letter 则与拒绝同一次原子动作完成,也不会出现 "已 republish 但还没
	 * ack"的双份窗口。
	 * @param definition 解析后的拓扑定义
	 * @return 重试拦截器
	 */
	private MethodInterceptor buildRetryAdvice(RabbitMqProperties.EntityDefinition definition) {
		return RetryInterceptorBuilder.stateless()
			.maxRetries(Math.max(definition.attempts() - 1, 0))
			.backOffOptions(definition.retryDelay().toMillis(), definition.retryMultiplier(),
					definition.retryMaxDelay().toMillis())
			.recoverer(rejectingMessageRecoverer())
			.build();
	}

	/**
	 * 重试耗尽后的恢复器:记下失败原因,然后抛出让容器拒绝这次投递的异常.
	 * <p>
	 * 关键在 {@code rejectManual=true}。容器用 {@code ContainerUtils#isRejectManual} 判断 manual
	 * ack 模式下要不要 拒绝,而 {@code AmqpRejectAndDontRequeueException} 的几个便捷构造函数把该标记设成了
	 * {@code false} —— 直接拿 Spring 自带的 {@code RejectAndDontRequeueRecoverer} 在 MANUAL
	 * 下什么都不会拒绝,消息会一直停在 unacked,直到通道 关闭才被重投(死信队列一份都收不到)。异常一旦抛出,容器就以 {@code requeue=false}
	 * 结算这次投递 ({@code ContainerUtils#shouldRequeue} 会强制它),Broker 随即 dead-letter。
	 * @return 恢复器
	 */
	private static MessageRecoverer rejectingMessageRecoverer() {
		return (message, cause) -> {
			logger.warn("Retry policy exhausted, rejecting the delivery so the broker dead-letters it", cause);
			throw new AmqpRejectAndDontRequeueException("Retry Policy Exhausted", true, cause);
		};
	}

	private RabbitMqProperties resolveProperties() {
		return requireProperties(requireBeanFactory().getBeanProvider(RabbitMqProperties.class).getIfAvailable());
	}

	private static RabbitMqProperties requireProperties(@Nullable RabbitMqProperties properties) {
		if (properties == null) {
			throw new IllegalStateException("No RabbitMqProperties bean available; it should have been registered by "
					+ "@EnableRabbitMqTemplates");
		}
		return properties;
	}

	private ConnectionFactory requireConnectionFactory() {
		return requireBean(requireBeanFactory().getBeanProvider(ConnectionFactory.class).getIfAvailable(),
				ConnectionFactory.class);
	}

	private static <T> T requireBean(@Nullable T bean, Class<T> type) {
		if (bean == null) {
			throw new IllegalStateException("No single " + type.getSimpleName()
					+ " bean available; configure spring.rabbitmq.* or define exactly one yourself");
		}
		return bean;
	}

	/**
	 * 取出容器中唯一的 {@link MessageConverter};没有或存在多个时返回 {@code null},由监听容器沿用自身默认值.
	 * @return 唯一的消息转换器,无法确定时返回 {@code null}
	 */
	private @Nullable MessageConverter resolveMessageConverter() {
		return requireBeanFactory().getBeanProvider(MessageConverter.class).getIfUnique();
	}

	/**
	 * 取出非空的 {@link BeanFactory}.
	 * <p>
	 * 字段本身可为空(容器注入之前),但注册器一旦开始使用就必须已有值,因此这里统一收口。
	 * @return 容器注入的 BeanFactory
	 * @throws IllegalStateException 容器尚未注入 BeanFactory 时抛出
	 */
	private BeanFactory requireBeanFactory() {
		BeanFactory factory = this.beanFactory;
		if (factory == null) {
			throw new IllegalStateException(
					"BeanFactory must not be null; " + getClass().getSimpleName() + " should be managed by Spring");
		}
		return factory;
	}

	private String beanName(Class<?> entityClass, String suffix) {
		return uncapitalize(entityClass.getSimpleName()) + suffix;
	}

	private String uncapitalize(String str) {
		if (str.isEmpty()) {
			return str;
		}
		return Character.toLowerCase(str.charAt(0)) + str.substring(1);
	}

}
