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
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;

/**
 * {@link EnableRocketMqTemplates} 的导入注册器:按实体类注册主题定义与绑定好主题的发送器.
 * <p>
 * 对 {@link EnableRocketMqTemplates#value()} 中的每个实体类注册两个 Bean:
 * <ul>
 * <li>{@code <实体>RocketMqTopic}:类型为 {@link RocketMqProperties.TopicDefinition},实例供应器里调用
 * {@link RocketMqProperties#resolve(Class)} 得到该实体类最终生效的名称与参数;</li>
 * <li>{@code <实体>RocketMqSender}:类型为 {@link RocketMqSender},复用容器中唯一的
 * {@link RocketMQClientTemplate},并绑定到该实体类的主主题。</li>
 * </ul>
 * <p>
 * 本注册器<b>不</b>注册 {@code RocketMQClientTemplate}:收发统一使用 starter 自动配置的那一个模板,避免按实体类 复制出多份
 * Producer 与连接。
 * <p>
 * 任一同名 Bean 已存在时跳过该实体类的对应注册,不覆盖使用方定义。
 * <p>
 * 同时实现 {@link BeanFactoryAware} 持有 {@link BeanFactory},以便在实例供应器里按类型取出模板与配置。
 * <p>
 * 本类的可空性只出现在字段、方法参数与方法返回值上,方法体内不声明可空的局部变量。
 */
public class RocketMqRegistrar implements ImportBeanDefinitionRegistrar, BeanFactoryAware {

	private static final String TOPIC_BEAN_NAME_SUFFIX = "RocketMqTopic";

	private static final String SENDER_BEAN_NAME_SUFFIX = "RocketMqSender";

	private @Nullable BeanFactory beanFactory;

	/**
	 * 记录当前 {@link BeanFactory},供注册 Bean 定义时在实例供应器内按类型查找模板.
	 * @param beanFactory spring 容器传入的 BeanFactory
	 * @throws BeansException spring 注入 BeanFactory 失败时抛出
	 */
	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
	}

	/**
	 * 读取标注 {@link EnableRocketMqTemplates} 的类的注解属性,并为其中的每个实体类注册主题定义与发送器.
	 * <p>
	 * 取不到 {@link EnableRocketMqTemplates} 的注解属性(结果为 {@code null})时不注册任何 Bean。
	 * @param importingClassMetadata 导入类(即标注 {@link EnableRocketMqTemplates} 的类)的注解元数据
	 * @param registry spring 的 Bean 定义注册表
	 */
	@Override
	public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
		AnnotationAttributes attributes = AnnotationAttributes
			.fromMap(importingClassMetadata.getAnnotationAttributes(EnableRocketMqTemplates.class.getName()));

		if (attributes == null) {
			return;
		}

		Class<?>[] entityClasses = attributes.getClassArray("value");

		for (Class<?> clazz : entityClasses) {
			registerTopicDefinition(registry, clazz);
			registerSender(registry, clazz);
		}
	}

	/**
	 * 注册该实体类的主题定义,供 {@link RocketMqTopicInitializer} 与使用方读取.
	 * @param registry spring 的 Bean 定义注册表
	 * @param entityClass {@link EnableRocketMqTemplates} 中声明的实体类
	 */
	private void registerTopicDefinition(BeanDefinitionRegistry registry, Class<?> entityClass) {
		String beanName = beanName(entityClass, TOPIC_BEAN_NAME_SUFFIX);

		if (registry.containsBeanDefinition(beanName)) {
			return;
		}

		RootBeanDefinition beanDefinition = new RootBeanDefinition();
		beanDefinition.setBeanClass(RocketMqProperties.TopicDefinition.class);
		beanDefinition.setTargetType(RocketMqProperties.TopicDefinition.class);
		beanDefinition.setInstanceSupplier(() -> resolveProperties().resolve(entityClass));

		registry.registerBeanDefinition(beanName, beanDefinition);
	}

	/**
	 * 注册该实体类专属的 {@link RocketMqSender},绑定到它的主主题.
	 * @param registry spring 的 Bean 定义注册表
	 * @param entityClass {@link EnableRocketMqTemplates} 中声明的实体类
	 */
	private void registerSender(BeanDefinitionRegistry registry, Class<?> entityClass) {
		String beanName = beanName(entityClass, SENDER_BEAN_NAME_SUFFIX);

		if (registry.containsBeanDefinition(beanName)) {
			return;
		}

		RootBeanDefinition beanDefinition = new RootBeanDefinition();
		beanDefinition.setBeanClass(RocketMqSender.class);
		beanDefinition.setTargetType(RocketMqSender.class);
		beanDefinition
			.setInstanceSupplier(() -> new RocketMqSender(requireTemplate(), resolveProperties().resolve(entityClass)));

		registry.registerBeanDefinition(beanName, beanDefinition);
	}

	private RocketMqProperties resolveProperties() {
		return requireProperties(requireBeanFactory().getBeanProvider(RocketMqProperties.class).getIfAvailable());
	}

	private static RocketMqProperties requireProperties(@Nullable RocketMqProperties properties) {
		if (properties == null) {
			throw new IllegalStateException("No RocketMqProperties bean available; it should have been registered by "
					+ "@EnableRocketMqTemplates");
		}
		return properties;
	}

	private RocketMQClientTemplate requireTemplate() {
		RocketMQClientTemplate template = requireBeanFactory().getBeanProvider(RocketMQClientTemplate.class)
			.getIfAvailable();
		if (template == null) {
			throw new IllegalStateException("No RocketMQClientTemplate bean available; configure "
					+ "rocketmq.producer.endpoints so the rocketmq-v5-client-spring-boot-starter registers one");
		}
		return template;
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
