package io.github.butterfly.sandbox.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import io.github.butterfly.core.BaseEnum;
import lombok.Getter;


@Getter
public enum HumidityType implements BaseEnum {

    /**
     * 正常
     */
    NORMAL(0, "正常（40%-60%）"),

    /**
     * 干燥
     */
    DRY(1, "干燥（<=40%）"),
    ;

    HumidityType(int code, String title) {
        this.code = code;
        this.title = title;
    }

    @EnumValue
    private final int code;

    private final String title;

    @Override
    public String toString() {
        return String.format("%s:%s", code, title);
    }


}
