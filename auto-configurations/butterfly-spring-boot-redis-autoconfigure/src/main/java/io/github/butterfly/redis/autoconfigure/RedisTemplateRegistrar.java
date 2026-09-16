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

package io.github.butterfly.redis.autoconfigure;

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
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.util.Assert;

/**
 * {@link EnableRedisTemplates} 的导入注册器:按实体类注册各自专用的 {@code RedisTemplate} Bean 定义.
 * <p>
 * 对 {@link EnableRedisTemplates#value()} 中的每个实体类注册一个 {@code RedisTemplate<String, T>} 类型的
 * Bean:
 * <ul>
 * <li>Bean 名称为实体类简单名首字母小写后拼接 {@code "RedisTemplate"},例如 {@code User} → {@code
 * userRedisTemplate};</li>
 * <li>key 与 hashKey 使用 {@link RedisSerializer#string()} 提供的 String 序列化器,value 与 hashValue
 * 使用 以该实体类为目标类型的 {@link JacksonJsonRedisSerializer};</li>
 * <li>若注册表中已存在同名 Bean 定义,则跳过该实体类,不覆盖已有定义。</li>
 * </ul>
 * <p>
 * 同时实现 {@link BeanFactoryAware} 持有 {@link BeanFactory},以便在 Bean 实例供应器中按类型取出
 * {@link RedisConnectionFactory} 并注入到模板上。
 */
public class RedisTemplateRegistrar implements ImportBeanDefinitionRegistrar, BeanFactoryAware {

	private @Nullable BeanFactory beanFactory;

	/**
	 * 记录当前 {@link BeanFactory},供注册 Bean 定义时在实例供应器内按类型查找 {@link RedisConnectionFactory}.
	 * @param beanFactory spring 容器传入的 BeanFactory
	 * @throws BeansException spring 注入 BeanFactory 失败时抛出
	 */
	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
	}

	/**
	 * 读取标注 {@link EnableRedisTemplates} 的类的注解属性,并为其中的每个实体类注册一个 {@code RedisTemplate} Bean
	 * 定义.
	 * <p>
	 * 若从导入类的元数据中取不到 {@link EnableRedisTemplates} 的注解属性(结果为 {@code null}),则不注册任何 Bean。
	 * @param importingClassMetadata 导入类(即标注 {@link EnableRedisTemplates} 的类)的注解元数据
	 * @param registry spring 的 Bean 定义注册表
	 */
	@Override
	public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
		AnnotationAttributes attributes = AnnotationAttributes
			.fromMap(importingClassMetadata.getAnnotationAttributes(EnableRedisTemplates.class.getName()));

		if (attributes == null) {
			return;
		}

		Class<?>[] entityClasses = attributes.getClassArray("value");

		for (Class<?> clazz : entityClasses) {
			registerCustomRedisTemplate(registry, clazz);
		}
	}

	private <T> void registerCustomRedisTemplate(BeanDefinitionRegistry registry, Class<T> entityClass) {

		String beanName = uncapitalize(entityClass.getSimpleName()) + "RedisTemplate";

		if (registry.containsBeanDefinition(beanName)) {
			return;
		}

		ResolvableType templateType = ResolvableType.forClassWithGenerics(RedisTemplate.class, String.class,
				entityClass);

		RootBeanDefinition beanDefinition = new RootBeanDefinition();
		beanDefinition.setBeanClass(RedisTemplate.class);
		beanDefinition.setTargetType(templateType);

		beanDefinition.setInstanceSupplier(() -> {
			Assert.notNull(this.beanFactory, "BeanFactory must not be null");
			RedisConnectionFactory connectionFactory = this.beanFactory.getBean(RedisConnectionFactory.class);

			RedisTemplate<String, T> template = new RedisTemplate<>();
			template.setConnectionFactory(connectionFactory);

			RedisSerializer<String> stringSerializer = RedisSerializer.string();
			template.setKeySerializer(stringSerializer);
			template.setHashKeySerializer(stringSerializer);

			JacksonJsonRedisSerializer<T> serializer = new JacksonJsonRedisSerializer<>(entityClass);
			template.setValueSerializer(serializer);
			template.setHashValueSerializer(serializer);

			template.afterPropertiesSet();
			return template;
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
