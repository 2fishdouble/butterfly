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

package io.github.butterfly.kafka.autoconfigure;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.retrytopic.DestinationTopic;
import org.springframework.kafka.retrytopic.RetryTopicConfiguration;
import org.springframework.kafka.retrytopic.RetryTopicConfigurationBuilder;
import org.springframework.kafka.support.Suffixer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * {@link EnableKafkaTemplates} 的导入注册器:按实体类注册各自专用的 {@code KafkaTemplate} Bean 定义.
 * <p>
 * 对 {@link EnableKafkaTemplates#value()} 中的每个实体类注册一组 Bean:
 * <ul>
 * <li>{@code KafkaTemplate<String, T>} 类型的模板,Bean 名称为实体类简单名首字母小写后拼接
 * {@code "KafkaTemplate"},例如 {@code User} → {@code userKafkaTemplate};key 使用
 * {@link StringSerializer},value 使用 {@link JacksonJsonSerializer},因此每个模板都按 JSON 写入, 与使用方在
 * {@code spring.kafka.producer.*} 上配置的序列化器相互独立。生产者参数从容器中已有的 {@link ProducerFactory}
 * 复制(bootstrap servers、安全认证、acks 等),仅覆盖序列化器;</li>
 * <li>同名 {@code NewTopic},由 {@code KafkaAdmin} 在启动时创建主主题:主题名默认取实体类简单名首字母小写, 分区数与副本数默认
 * {@code 1},三者均可在 {@code butterfly.kafka.topic.*} 下按实体类覆盖;</li>
 * <li>{@link RetryTopicConfiguration},让该实体主题上的 {@code @KafkaListener} <b>无需</b>标注
 * {@code @RetryableTopic} 即默认获得非阻塞重试与死信投递能力,重试参数(次数、退避、命名策略、死信处理器等) 同样取自
 * {@code butterfly.kafka.topic.*};</li>
 * <li>{@link KafkaAdmin.NewTopics},把重试主题({@code <主题>-retry-<延迟>})与死信主题({@code <主题>-dlt})
 * 一并登记给 {@code KafkaAdmin},因此实体主题建立时对应的重试与死信主题也随之建立。</li>
 * </ul>
 * <p>
 * 重试主题的名称与 Spring Kafka 自身完全一致:注册器不再自行拼接后缀,而是用
 * {@link RetryTopicConfiguration#getDestinationTopicProperties()} 得到每个目标主题的
 * {@link DestinationTopic.Properties#suffix()},再交给 {@link Suffixer} 拼接,从而与运行时实际监听的主题
 * 严格对齐。
 * <p>
 * 任一同名 Bean 已存在时跳过该实体类的对应注册,不覆盖使用方定义:自定义同名 {@code NewTopic} 可接管主题, 自定义同名
 * {@code RetryTopicConfiguration} 可完全接管重试策略,而在监听方法上标注 {@code @RetryableTopic}
 * 则以注解为准(注解优先于上下文中的配置)。
 * <p>
 * 同时实现 {@link BeanFactoryAware} 持有 {@link BeanFactory},以便在 Bean 实例供应器中按类型取出
 * {@link ProducerFactory} 与 {@link KafkaTopicProperties}。本类的可空性只出现在字段、方法参数与方法返回值上,
 * 方法体内不声明可空的局部变量。
 */
public class KafkaTemplateRegistrar implements ImportBeanDefinitionRegistrar, BeanFactoryAware {

	private static final String TEMPLATE_BEAN_NAME_SUFFIX = "KafkaTemplate";

	private static final String NEW_TOPIC_BEAN_NAME_SUFFIX = "NewTopic";

	private static final String RETRY_TOPICS_BEAN_NAME_SUFFIX = "RetryTopics";

	private static final String RETRY_CONFIGURATION_BEAN_NAME_SUFFIX = "RetryTopicConfiguration";

	private static final String DLT_HANDLER_SEPARATOR = "#";

	private @Nullable BeanFactory beanFactory;

	/**
	 * 记录当前 {@link BeanFactory},供注册 Bean 定义时在实例供应器内按类型查找 {@link ProducerFactory}.
	 * @param beanFactory spring 容器传入的 BeanFactory
	 * @throws BeansException spring 注入 BeanFactory 失败时抛出
	 */
	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
	}

	/**
	 * 读取标注 {@link EnableKafkaTemplates} 的类的注解属性,并为其中的每个实体类注册模板、主题与重试相关 Bean 定义.
	 * <p>
	 * 若从导入类的元数据中取不到 {@link EnableKafkaTemplates} 的注解属性(结果为 {@code null}),则不注册任何 Bean。
	 * @param importingClassMetadata 导入类(即标注 {@link EnableKafkaTemplates} 的类)的注解元数据
	 * @param registry spring 的 Bean 定义注册表
	 */
	@Override
	public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
		AnnotationAttributes attributes = AnnotationAttributes
			.fromMap(importingClassMetadata.getAnnotationAttributes(EnableKafkaTemplates.class.getName()));

		if (attributes == null) {
			return;
		}

		Class<?>[] entityClasses = attributes.getClassArray("value");

		for (Class<?> clazz : entityClasses) {
			registerCustomKafkaTemplate(registry, clazz);
			registerNewTopic(registry, clazz);
			registerRetryTopicConfiguration(registry, clazz);
			registerRetryTopics(registry, clazz);
		}
	}

	private <T> void registerNewTopic(BeanDefinitionRegistry registry, Class<T> entityClass) {

		String beanName = beanName(entityClass, NEW_TOPIC_BEAN_NAME_SUFFIX);

		if (registry.containsBeanDefinition(beanName)) {
			return;
		}

		RootBeanDefinition beanDefinition = new RootBeanDefinition();
		beanDefinition.setBeanClass(NewTopic.class);
		beanDefinition.setTargetType(NewTopic.class);

		beanDefinition.setInstanceSupplier(() -> {
			KafkaTopicProperties properties = resolveTopicProperties();
			KafkaTopicProperties.TopicDefinition definition = properties.resolve(entityClass);

			return TopicBuilder.name(definition.name())
				.partitions(definition.partitions())
				.replicas(definition.replicas())
				.build();
		});

		registry.registerBeanDefinition(beanName, beanDefinition);
	}

	/**
	 * 注册该实体类的 {@link RetryTopicConfiguration},让该主题上的监听方法默认获得重试与死信能力.
	 * <p>
	 * 只要监听方法落在该实体主题上,且未标注 {@code @RetryableTopic},Spring Kafka 就会用这份配置建立重试 与死信链路。
	 * @param <T> 实体类类型
	 * @param registry spring 的 Bean 定义注册表
	 * @param entityClass {@link EnableKafkaTemplates} 中声明的实体类
	 */
	private <T> void registerRetryTopicConfiguration(BeanDefinitionRegistry registry, Class<T> entityClass) {

		String beanName = beanName(entityClass, RETRY_CONFIGURATION_BEAN_NAME_SUFFIX);

		if (registry.containsBeanDefinition(beanName)) {
			return;
		}

		RootBeanDefinition beanDefinition = new RootBeanDefinition();
		beanDefinition.setBeanClass(RetryTopicConfiguration.class);
		beanDefinition.setTargetType(RetryTopicConfiguration.class);
		beanDefinition.setInstanceSupplier(() -> buildRetryTopicConfiguration(entityClass));

		registry.registerBeanDefinition(beanName, beanDefinition);
	}

	/**
	 * 注册该实体类的重试主题与死信主题.
	 * <p>
	 * {@link KafkaAdmin.NewTopics} 允许一个 Bean 携带不定数量的 {@link NewTopic},因此重试主题的个数可以随
	 * 重试次数变化,无需在注册期确定。
	 * @param <T> 实体类类型
	 * @param registry spring 的 Bean 定义注册表
	 * @param entityClass {@link EnableKafkaTemplates} 中声明的实体类
	 */
	private <T> void registerRetryTopics(BeanDefinitionRegistry registry, Class<T> entityClass) {

		String beanName = beanName(entityClass, RETRY_TOPICS_BEAN_NAME_SUFFIX);

		if (registry.containsBeanDefinition(beanName)) {
			return;
		}

		RootBeanDefinition beanDefinition = new RootBeanDefinition();
		beanDefinition.setBeanClass(KafkaAdmin.NewTopics.class);
		beanDefinition.setTargetType(KafkaAdmin.NewTopics.class);
		beanDefinition.setInstanceSupplier(() -> buildRetryTopics(entityClass));

		registry.registerBeanDefinition(beanName, beanDefinition);
	}

	private <T> RetryTopicConfiguration buildRetryTopicConfiguration(Class<T> entityClass) {
		KafkaTopicProperties properties = resolveTopicProperties();
		KafkaTopicProperties.TopicDefinition topic = properties.resolve(entityClass);
		KafkaTopicProperties.RetryDefinition retry = properties.resolveRetry(entityClass);

		// resolveRetry 已把未配置项填成内置默认值,这里不必再逐个判空
		RetryTopicConfigurationBuilder builder = RetryTopicConfigurationBuilder.newInstance()
			.includeTopic(topic.name())
			.maxAttempts(retry.attempts())
			.retryTopicSuffix(retry.retryTopicSuffix())
			.dltSuffix(retry.dltTopicSuffix())
			.setTopicSuffixingStrategy(retry.topicSuffixingStrategy())
			.sameIntervalTopicReuseStrategy(retry.sameIntervalTopicReuseStrategy())
			.autoStartDltHandler(retry.autoStartDltHandler())
			// 重试与死信主题由本注册器登记的 NewTopics 负责创建,避免与运行时自动建主题重复
			.doNotAutoCreateRetryTopics();

		// 未配置死信处理器时用空串占位,hasText 判空后即可安全解析
		String dltHandler = Objects.requireNonNullElse(retry.dltHandler(), "");
		if (StringUtils.hasText(dltHandler)) {
			builder.dltHandlerMethod(dltHandlerBeanName(dltHandler), dltHandlerMethodName(dltHandler));
		}

		long delayMillis = retry.delay().toMillis();
		long maxDelayMillis = retry.maxDelay().toMillis();
		if (retry.multiplier() > 1.0 && maxDelayMillis > delayMillis) {
			builder.exponentialBackoff(delayMillis, retry.multiplier(), maxDelayMillis);
		}
		else {
			// 倍率不大于 1,或上限不超过起始间隔时,间隔实际恒定,按固定退避处理
			builder.fixedBackOff(delayMillis);
		}

		return builder.create(resolveKafkaOperations(entityClass));
	}

	private <T> KafkaAdmin.NewTopics buildRetryTopics(Class<T> entityClass) {
		KafkaTopicProperties properties = resolveTopicProperties();
		KafkaTopicProperties.TopicDefinition topic = properties.resolve(entityClass);
		KafkaTopicProperties.RetryDefinition retry = properties.resolveRetry(entityClass);
		RetryTopicConfiguration configuration = resolveRetryTopicConfiguration(entityClass);

		NewTopic[] newTopics = configuration.getDestinationTopicProperties()
			.stream()
			.filter((destination) -> !destination.isMainEndpoint())
			.map((destination) -> TopicBuilder.name(suffixedTopicName(topic.name(), destination.suffix()))
				.partitions(retry.partitions())
				.replicas(retry.replicas())
				.build())
			.toArray(NewTopic[]::new);

		return new KafkaAdmin.NewTopics(newTopics);
	}

	/**
	 * 按 Spring Kafka 自身的后缀规则拼出目标主题名.
	 * <p>
	 * {@link Suffixer#maybeAddTo(String)} 在 Spring 侧被标注为可空入参加可空返回值(因为它对空主题名原样返回),
	 * 而这里传入的主题名必定非空,所以用 {@link Objects#requireNonNullElse} 把它的可空返回值收成非空。
	 * @param topicName 主主题名,必定非空
	 * @param suffix 目标主题后缀,取自 {@link DestinationTopic.Properties#suffix()}
	 * @return 目标主题名,必定非空
	 */
	private static String suffixedTopicName(String topicName, String suffix) {
		return Objects.requireNonNullElse(new Suffixer(suffix).maybeAddTo(topicName), topicName);
	}

	private KafkaOperations<?, ?> resolveKafkaOperations(Class<?> entityClass) {
		BeanFactory factory = requireBeanFactory();
		String templateBeanName = beanName(entityClass, TEMPLATE_BEAN_NAME_SUFFIX);
		if (factory.containsBean(templateBeanName)) {
			return factory.getBean(templateBeanName, KafkaOperations.class);
		}
		return requireKafkaOperations(factory.getBeanProvider(KafkaOperations.class).getIfUnique(), entityClass,
				templateBeanName);
	}

	private static KafkaOperations<?, ?> requireKafkaOperations(@Nullable KafkaOperations<?, ?> operations,
			Class<?> entityClass, String templateBeanName) {
		if (operations == null) {
			throw new IllegalStateException("No KafkaOperations bean available for " + entityClass.getName()
					+ "; expected a bean named '" + templateBeanName + "'");
		}
		return operations;
	}

	private RetryTopicConfiguration resolveRetryTopicConfiguration(Class<?> entityClass) {
		BeanFactory factory = requireBeanFactory();
		String retryConfigurationBeanName = beanName(entityClass, RETRY_CONFIGURATION_BEAN_NAME_SUFFIX);
		if (factory.containsBean(retryConfigurationBeanName)) {
			return factory.getBean(retryConfigurationBeanName, RetryTopicConfiguration.class);
		}
		return buildRetryTopicConfiguration(entityClass);
	}

	private String dltHandlerBeanName(String dltHandler) {
		return splitDltHandler(dltHandler)[0];
	}

	private String dltHandlerMethodName(String dltHandler) {
		return splitDltHandler(dltHandler)[1];
	}

	private String[] splitDltHandler(String dltHandler) {
		int separatorIndex = dltHandler.indexOf(DLT_HANDLER_SEPARATOR);
		if (separatorIndex <= 0 || separatorIndex == dltHandler.length() - 1) {
			throw new IllegalStateException("Invalid 'butterfly.kafka.topic.*.retry.dlt-handler' value '" + dltHandler
					+ "'; expected the form 'beanName" + DLT_HANDLER_SEPARATOR + "methodName'");
		}
		return new String[] { dltHandler.substring(0, separatorIndex), dltHandler.substring(separatorIndex + 1) };
	}

	private KafkaTopicProperties resolveTopicProperties() {
		return requireTopicProperties(
				requireBeanFactory().getBeanProvider(KafkaTopicProperties.class).getIfAvailable());
	}

	private static KafkaTopicProperties requireTopicProperties(@Nullable KafkaTopicProperties properties) {
		if (properties == null) {
			throw new IllegalStateException("No KafkaTopicProperties bean available; it should have been registered by "
					+ "@EnableKafkaTemplates");
		}
		return properties;
	}

	private <T> void registerCustomKafkaTemplate(BeanDefinitionRegistry registry, Class<T> entityClass) {

		String beanName = beanName(entityClass, TEMPLATE_BEAN_NAME_SUFFIX);

		if (registry.containsBeanDefinition(beanName)) {
			return;
		}

		ResolvableType templateType = ResolvableType.forClassWithGenerics(KafkaTemplate.class, String.class,
				entityClass);

		RootBeanDefinition beanDefinition = new RootBeanDefinition();
		beanDefinition.setBeanClass(KafkaTemplate.class);
		beanDefinition.setTargetType(templateType);

		beanDefinition.setInstanceSupplier(() -> {
			ProducerFactory<?, ?> sharedProducerFactory = requireProducerFactory(
					requireBeanFactory().getBeanProvider(ProducerFactory.class).getIfAvailable());

			Map<String, Object> configs = new HashMap<>(sharedProducerFactory.getConfigurationProperties());
			configs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
			configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);

			DefaultKafkaProducerFactory<String, T> producerFactory = new DefaultKafkaProducerFactory<>(configs);

			return new KafkaTemplate<>(producerFactory);
		});

		registry.registerBeanDefinition(beanName, beanDefinition);
	}

	private static ProducerFactory<?, ?> requireProducerFactory(@Nullable ProducerFactory<?, ?> producerFactory) {
		if (producerFactory == null) {
			throw new IllegalStateException("No ProducerFactory bean available; configure spring.kafka.* or define a "
					+ "ProducerFactory bean");
		}
		return producerFactory;
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
