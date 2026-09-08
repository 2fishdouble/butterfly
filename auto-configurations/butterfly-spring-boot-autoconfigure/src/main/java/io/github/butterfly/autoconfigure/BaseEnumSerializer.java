package io.github.butterfly.autoconfigure;

import io.github.butterfly.core.BaseEnum;
import jakarta.annotation.Nullable;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;


public class BaseEnumSerializer extends ValueSerializer<BaseEnum> {

    @Override
    public void serialize(@Nullable BaseEnum value, JsonGenerator gen, SerializationContext serializers) {
        if (value == null) {
            gen.writeNull();
        } else {
            gen.writeNumber(value.getCode());
        }
    }
}