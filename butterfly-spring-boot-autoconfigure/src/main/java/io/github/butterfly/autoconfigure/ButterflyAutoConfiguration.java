package io.github.butterfly.autoconfigure;

import io.github.butterfly.GreetingService;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(prefix = "butterfly", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ButterflyProperties.class)
public class ButterflyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(GreetingService.class)
    public GreetingService greetingService(ButterflyProperties properties) {
        return new GreetingService(properties.getGreeting());
    }
}
