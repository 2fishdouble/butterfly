package io.github.butterfly.autoconfigure;


import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(prefix = "butterfly", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ButterflyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(SpelSup.class)
    public SpelSup spelSup(BeanFactory beanFactory) {
        return new SpelSup(beanFactory);
    }
}
