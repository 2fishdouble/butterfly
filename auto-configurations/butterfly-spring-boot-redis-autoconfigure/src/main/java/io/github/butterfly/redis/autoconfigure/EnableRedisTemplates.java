package io.github.butterfly.redis.autoconfigure;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(RedisTemplateRegistrar.class)
public @interface EnableRedisTemplates {

    Class<?>[] value() default {};
}