package io.github.butterfly.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 前缀 butterfly 的配置绑定：
 * <pre>
 * butterfly.enabled=true
 * butterfly.greeting=你好
 * </pre>
 */
@ConfigurationProperties(prefix = "butterfly")
public class ButterflyProperties {

    /** 是否启用 butterfly 自动装配，默认开启 */
    private boolean enabled = true;

    /** 问候语模板，默认 Hello */
    private String greeting = "Hello";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getGreeting() {
        return greeting;
    }

    public void setGreeting(String greeting) {
        this.greeting = greeting;
    }
}
