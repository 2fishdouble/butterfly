package io.github.butterfly.autoconfigure;

import io.github.butterfly.core.BaseEnum;
import jakarta.annotation.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ValueDeserializer;

public class BaseEnumDeserializer extends ValueDeserializer<BaseEnum> {

    @Nullable
    private final Class<? extends BaseEnum> enumType;

    public BaseEnumDeserializer() {
        this.enumType = null;
    }

    public BaseEnumDeserializer(Class<? extends BaseEnum> enumType) {
        this.enumType = enumType;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ValueDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
        JavaType type = ctxt.getContextualType();
        if (type != null && BaseEnum.class.isAssignableFrom(type.getRawClass())) {
            return new BaseEnumDeserializer((Class<? extends BaseEnum>) type.getRawClass());
        }
        return this;
    }

    @Override
    public @Nullable BaseEnum deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
        if (this.enumType == null) {
            return null;
        }
        String v = p.getString();
        if (v == null || v.trim().isEmpty()) {
            return null;
        }

        try {
            return BaseEnumJsonFactory.parse(this.enumType, v.trim());
        } catch (IllegalArgumentException e) {
            throw ctxt.weirdStringException(v, this.enumType, e.getMessage());
        }
    }


}