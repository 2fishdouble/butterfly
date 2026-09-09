package io.github.butterfly.mail.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * 注册基于 {@link JavaMailSender} 的 {@link MailTemplate} 门面。
 * <p>
 * 须在 Boot 的 {@link MailSenderAutoConfiguration} 之后执行;
 * 只有存在可用的 {@link JavaMailSender} bean 时才注册,否则优雅跳过。
 */
@AutoConfiguration(after = MailSenderAutoConfiguration.class)
@EnableConfigurationProperties(MailTemplateProperties.class)
@ConditionalOnClass({MailSenderAutoConfiguration.class, JavaMailSender.class})
public class ButterflyMailAutoConfiguration {

    @Bean
    @ConditionalOnBean(JavaMailSender.class)
    @ConditionalOnMissingBean(MailTemplate.class)
    public MailTemplate mailTemplate(JavaMailSender mailSender, MailTemplateProperties properties) {
        return new MailTemplate(mailSender, properties.getDefaultFrom());
    }
}
