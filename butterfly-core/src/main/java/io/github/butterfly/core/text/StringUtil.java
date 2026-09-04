package io.github.butterfly.core.text;

/**
 * 字符串处理工具。
 *
 * <p>设计为普通对象（实例方法）并注册为 Bean，便于在使用方直接注入；
 * 所有方法对 {@code null} 均安全。
 */
public class StringUtil {

    /** 判断字符串是否为 null、空串或纯空白 */
    public boolean isBlank(CharSequence text) {
        if (text == null || text.isEmpty()) {
            return true;
        }
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** {@link #isBlank} 的取反 */
    public boolean isNotBlank(CharSequence text) {
        return !isBlank(text);
    }

    /** 截断到指定最大长度；不足或相等时原样返回，{@code null} 返回 {@code null} */
    public String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    /** 首字母大写，其余不变；{@code null} 或空白返回原值 */
    public String capitalize(String text) {
        if (isBlank(text)) {
            return text;
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
