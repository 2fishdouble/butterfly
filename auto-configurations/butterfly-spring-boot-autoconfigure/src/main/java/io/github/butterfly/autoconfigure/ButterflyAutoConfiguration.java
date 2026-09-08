package io.github.butterfly.autoconfigure;


import io.github.butterfly.core.BaseEnum;
import io.github.butterfly.core.PatternConstant;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ext.javatime.deser.LocalDateDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalTimeDeserializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalTimeSerializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@AutoConfiguration
public class ButterflyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(SpelSup.class)
    public SpelSup spelSup(BeanFactory beanFactory) {
        return new SpelSup(beanFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    public JsonMapperBuilderCustomizer jsonMapperBuilderCustomizer() {
        return builder -> {
            SimpleModule baseEnumModule = new SimpleModule();
            baseEnumModule.addDeserializer(BaseEnum.class, new BaseEnumDeserializer());
            baseEnumModule.addSerializer(BaseEnum.class, new BaseEnumSerializer());
            builder.addModule(baseEnumModule);

            SimpleModule longModule = new SimpleModule();
            longModule.addSerializer(Long.class, ToStringSerializer.instance);
            longModule.addSerializer(Long.TYPE, ToStringSerializer.instance);
            longModule.addSerializer(long.class, ToStringSerializer.instance);
            builder.addModule(longModule);

            SimpleModule timeModule = new SimpleModule();
            timeModule.addDeserializer(LocalDateTime.class,
                    new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(PatternConstant.DATE_TIME_FORMAT)));
            timeModule.addDeserializer(LocalDate.class,
                    new LocalDateDeserializer(DateTimeFormatter.ofPattern(PatternConstant.DATE_FORMAT)));
            timeModule.addDeserializer(LocalTime.class,
                    new LocalTimeDeserializer(DateTimeFormatter.ofPattern(PatternConstant.TIME_FORMAT)));
            timeModule.addSerializer(LocalDateTime.class,
                    new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(PatternConstant.DATE_TIME_FORMAT)));
            timeModule.addSerializer(LocalDate.class,
                    new LocalDateSerializer(DateTimeFormatter.ofPattern(PatternConstant.DATE_FORMAT)));
            timeModule.addSerializer(LocalTime.class,
                    new LocalTimeSerializer(DateTimeFormatter.ofPattern(PatternConstant.TIME_FORMAT)));
            builder.addModule(timeModule);
        };
    }
}
