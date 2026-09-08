package io.github.butterfly.autoconfigure;

import io.github.butterfly.core.BaseEnum;
import jakarta.annotation.Nullable;


public final class BaseEnumJsonFactory {

    private BaseEnumJsonFactory() {
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public static <T extends BaseEnum> T parse(
            Class<? extends BaseEnum> enumType,
            @Nullable Object rawValue
    ) {
        if (rawValue == null) {
            return null;
        }

        String value = String.valueOf(rawValue).trim();
        if (value.isEmpty()) {
            return null;
        }

        if (!enumType.isEnum()) {
            throw new IllegalArgumentException(
                    "类型 %s 不是枚举".formatted(enumType.getName())
            );
        }

        for (T item : (T[]) enumType.getEnumConstants()) {
            if (value.equals(String.valueOf(item.getCode()))
                    || value.equalsIgnoreCase(item.getTitle())
                    || value.equalsIgnoreCase(((Enum<?>) item).name())) {
                return item;
            }
        }

        throw new IllegalArgumentException(
                "无法将值 [%s] 解析为枚举 %s"
                        .formatted(value, enumType.getSimpleName())
        );
    }
}
