package io.github.butterfly.mail.autoconfigure;

import lombok.Data;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 邮件模板配置,前缀 {@code butterfly.mail}。
 */
@Data
@ConfigurationProperties(prefix = "butterfly.mail")
public class MailTemplateProperties {

    /**
     * 默认发件人(From)。发件时优先取它,其次取 JavaMailSender 的 username。
     * 两者都为空时交给底层 SMTP/JNDI 会话处理。
     */
    @Nullable
    private String defaultFrom;
}
