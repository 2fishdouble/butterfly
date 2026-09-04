package io.github.butterfly.core.time;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 时间格式化工具。
 *
 * <p>普通对象（实例方法）并可注册为 Bean；默认格式 {@code yyyy-MM-dd HH:mm:ss}，
 * 也可通过构造参数定制。
 */
public class TimeUtil {

    private final String pattern;
    private final DateTimeFormatter formatter;

    public TimeUtil() {
        this("yyyy-MM-dd HH:mm:ss");
    }

    public TimeUtil(String pattern) {
        this.pattern = pattern;
        this.formatter = DateTimeFormatter.ofPattern(pattern);
    }

    /** 按配置格式格式化时间 */
    public String format(LocalDateTime time) {
        return time.format(formatter);
    }

    /** 当前时间按配置格式格式化 */
    public String now() {
        return format(LocalDateTime.now());
    }

    /** 当前使用的格式模式 */
    public String pattern() {
        return pattern;
    }
}
