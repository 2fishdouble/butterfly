package io.github.butterfly.redis.autoconfigure;

import jakarta.annotation.Nullable;
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

public class RedisTemplateRegistrar implements ImportBeanDefinitionRegistrar, BeanFactoryAware {

    @Nullable
    private BeanFactory beanFactory;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        AnnotationAttributes attributes = AnnotationAttributes.fromMap(
                importingClassMetadata.getAnnotationAttributes(EnableRedisTemplates.class.getName())
        );

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

        ResolvableType templateType = ResolvableType.forClassWithGenerics(RedisTemplate.class, String.class, entityClass);

        RootBeanDefinition beanDefinition = new RootBeanDefinition();
        beanDefinition.setBeanClass(RedisTemplate.class);
        beanDefinition.setTargetType(templateType);


        beanDefinition.setInstanceSupplier(() -> {
            Assert.notNull(beanFactory, "BeanFactory must not be null");
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