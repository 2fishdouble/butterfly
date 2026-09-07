package io.github.butterfly.autoconfigure;


import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class ButterflyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(SpelSup.class)
    public SpelSup spelSup(BeanFactory beanFactory) {
        return new SpelSup(beanFactory);
    }
}
