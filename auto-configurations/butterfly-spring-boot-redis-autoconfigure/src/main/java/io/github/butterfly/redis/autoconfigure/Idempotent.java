package io.github.butterfly.redis.autoconfigure;

import java.lang.annotation.*;



@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {
    String[] lockValue() default "";
}

