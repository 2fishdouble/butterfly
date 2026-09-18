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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link EnableKafkaTemplates} 的导入注册器:按实体类注册各自专用的 {@code KafkaTemplate} Bean 定义.
 * <p>
 * 对 {@link EnableKafkaTemplates#value()} 中的每个实体类注册一个 {@code KafkaTemplate<String, T>} 类型的
 * Bean:
 * <ul>
 * <li>Bean 名称为实体类简单名首字母小写后拼接 {@code "KafkaTemplate"},例如 {@code User} →
 * {@code userKafkaTemplate};</li>
 * <li>key 使用 {@link StringSerializer},value 使用 {@link JacksonJsonSerializer},因此每个模板都按
 * JSON 写入, 与使用方在 {@code spring.kafka.producer.*} 上配置的序列化器相互独立;</li>
 * <li>生产者参数从容器中已有的 {@link ProducerFactory} 复制(bootstrap servers、安全认证、acks 等),
 * 仅覆盖序列化器,避免重复维护连接配置;</li>
 * <li>若注册表中已存在同名 Bean 定义,则跳过该实体类,不覆盖已有定义。</li>
 * </ul>
 * <p>
 * 同名的 {@code NewTopic} Bean 也会一并注册,由 {@code KafkaAdmin} 在启动时创建主题:主题名默认取实体类简单名
 * 首字母小写,分区数与副本数默认 {@code 1},三者均可在 {@code butterfly.kafka.topic.*} 下按实体类覆盖,详见
 * {@link KafkaTopicProperties}。使用方自行定义同名 {@code NewTopic} Bean 即可接管该主题。
 * <p>
 * 同时实现 {@link BeanFactoryAware} 持有 {@link BeanFactory},以便在 Bean 实例供应器中按类型取出
 * {@link ProducerFactory} 与 {@link KafkaTopicProperties}。
 */
public class KafkaTemplateRegistrar implements ImportBeanDefinitionRegistrar, BeanFactoryAware {

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
	 * 读取标注 {@link EnableKafkaTemplates} 的类的注解属性,并为其中的每个实体类注册一个 {@code KafkaTemplate} Bean
	 * 定义.
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
		}
	}

	private <T> void registerNewTopic(BeanDefinitionRegistry registry, Class<T> entityClass) {

		String beanName = uncapitalize(entityClass.getSimpleName()) + "NewTopic";

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

	private KafkaTopicProperties resolveTopicProperties() {
		Assert.notNull(this.beanFactory, "BeanFactory must not be null");
		KafkaTopicProperties properties = this.beanFactory.getBeanProvider(KafkaTopicProperties.class).getIfAvailable();
		if (properties == null) {
			throw new IllegalStateException("No KafkaTopicProperties bean available; it should have been registered by "
					+ "@EnableKafkaTemplates");
		}
		return properties;
	}

	private <T> void registerCustomKafkaTemplate(BeanDefinitionRegistry registry, Class<T> entityClass) {

		String beanName = uncapitalize(entityClass.getSimpleName()) + "KafkaTemplate";

		if (registry.containsBeanDefinition(beanName)) {
			return;
		}

		ResolvableType templateType = ResolvableType.forClassWithGenerics(KafkaTemplate.class, String.class,
				entityClass);

		RootBeanDefinition beanDefinition = new RootBeanDefinition();
		beanDefinition.setBeanClass(KafkaTemplate.class);
		beanDefinition.setTargetType(templateType);

		beanDefinition.setInstanceSupplier(() -> {
			Assert.notNull(this.beanFactory, "BeanFactory must not be null");
			ProducerFactory<?, ?> sharedProducerFactory = this.beanFactory.getBeanProvider(ProducerFactory.class)
				.getIfAvailable();
			if (sharedProducerFactory == null) {
				throw new IllegalStateException(
						"No ProducerFactory bean available; configure spring.kafka.* or define a "
								+ "ProducerFactory bean");
			}

			Map<String, Object> configs = new HashMap<>(sharedProducerFactory.getConfigurationProperties());
			configs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
			configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);

			DefaultKafkaProducerFactory<String, T> producerFactory = new DefaultKafkaProducerFactory<>(configs);

			return new KafkaTemplate<>(producerFactory);
		});

		registry.registerBeanDefinition(beanName, beanDefinition);
	}

	private String uncapitalize(String str) {
		if (str.isEmpty()) {
			return str;
		}
		return Character.toLowerCase(str.charAt(0)) + str.substring(1);
	}

}
