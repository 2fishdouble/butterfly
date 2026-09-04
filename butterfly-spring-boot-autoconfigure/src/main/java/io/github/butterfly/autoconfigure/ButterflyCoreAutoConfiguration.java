package io.github.butterfly.autoconfigure;

import io.github.butterfly.core.id.IdGenerator;
import io.github.butterfly.core.id.UuidIdGenerator;
import io.github.butterfly.core.text.StringUtil;
import io.github.butterfly.core.time.TimeUtil;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 把 butterfly-core 中的工具类注册为 Bean，使用方可直接注入使用。
 *
 * <p>受总开关 {@code butterfly.enabled} 控制（默认开启）；每个工具 Bean 都带
 * {@link ConditionalOnMissingBean}，使用者可通过提供同名类型 Bean 来覆盖默认实现。
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "butterfly", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ButterflyCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public StringUtil stringUtil() {
        return new StringUtil();
    }

    @Bean
    @ConditionalOnMissingBean
    public IdGenerator idGenerator() {
        return new UuidIdGenerator();
    }

    @Bean
    @ConditionalOnMissingBean
    public TimeUtil timeUtil() {
        return new TimeUtil();
    }
}
