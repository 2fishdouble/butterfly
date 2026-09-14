package io.github.butterfly.redis.autoconfigure;

import io.github.butterfly.autoconfigure.SpelSup;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Redis 相关自动配置。
 *
 * @see org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration
 */
@AutoConfiguration
@ConditionalOnClass(RedissonClient.class)
public class ButterflyRedisConfiguration {

    /**
     * {@code @Idempotent} 幂等切面。
     * <p>
     * 此前它被直接写进 {@code AutoConfiguration.imports}(但它只是个 {@code @Component});
     * 现改为标准 {@code @Bean} 注册,并把 AspectJ / hutool 的存在性条件下到方法上,
     * 缺少任一依赖时优雅跳过,而不是抛 NoClassDefFoundError。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = {
            "org.aspectj.lang.ProceedingJoinPoint",
            "cn.hutool.core.lang.Validator"})
    public IdempotentAspect idempotentAspect(SpelSup spelSup, RedissonClient redissonClient) {
        return new IdempotentAspect(spelSup, redissonClient);
    }
}