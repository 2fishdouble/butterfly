package io.github.butterfly.core;

import lombok.Data;

import java.io.Serializable;


@Data
public class R<T> implements Serializable {
    private int code;

    private Boolean isSuccess;

    private String msg;

    private T data;

    public static <T> R<T> success(T t) {
        R<T> resultBase = new R<>();
        resultBase.setCode(200);
        resultBase.setIsSuccess(true);
        resultBase.setData(t);
        return resultBase;
    }

    public static <T> R<T> error(String errorInfo) {
        R<T> resultBase = new R<>();
        resultBase.setCode(100000);
        resultBase.setIsSuccess(false);
        resultBase.setMsg(errorInfo);
        return resultBase;
    }

    public R() {
    }

    public R(T t) {
        this.code = 200;
        this.isSuccess = true;
        this.data = t;
    }

}
