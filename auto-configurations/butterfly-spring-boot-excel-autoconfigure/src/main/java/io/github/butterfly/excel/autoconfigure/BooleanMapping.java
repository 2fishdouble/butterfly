package io.github.butterfly.excel.autoconfigure;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface BooleanMapping {
    String trueValue();
    String falseValue();
    String nullValue() default "";
}
