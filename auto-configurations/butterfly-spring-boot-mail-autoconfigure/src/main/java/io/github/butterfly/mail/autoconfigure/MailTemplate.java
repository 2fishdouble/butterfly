package io.github.butterfly.mail.autoconfigure;

import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.StreamSupport;

/**
 * 基于 {@link JavaMailSender} 的邮件发送门面,支持发送纯文本与携带附件。
 * <p>
 * 底层依赖 Spring Boot {@code MailSenderAutoConfiguration} 生成的
 * {@link JavaMailSender} bean(需配置 {@code spring.mail.host} 或 JNDI),
 * 因此仅在有发件能力时才被注册。
 */
public final class MailTemplate {

    private static final String DEFAULT_ENCODING = StandardCharsets.UTF_8.name();

    private final JavaMailSender mailSender;
    private final @Nullable String defaultFrom;

    public MailTemplate(JavaMailSender mailSender, @Nullable String defaultFrom) {
        this.mailSender = Objects.requireNonNull(mailSender, "mailSender must not be null");
        this.defaultFrom = defaultFrom;
    }

    /**
     * 发送纯文本邮件。
     *
     * @param to      收件人
     * @param subject 主题
     * @param text    正文
     */
    public void sendText(String to, String subject, String text) {
        sendText(List.of(to), subject, text);
    }

    /**
     * 群发纯文本邮件。
     *
     * @param to      收件人列表
     * @param subject 主题
     * @param text    正文
     */
    public void sendText(Iterable<String> to, String subject, String text) {
        send(to, subject, text, Map.of());
    }

    /**
     * 发送携带附件的邮件,正文为纯文本。
     *
     * @param to          收件人
     * @param subject     主题
     * @param text        正文
     * @param attachments 附件名 → 附件内容,{@link Resource} 覆盖 File/byte[]/classpath 等场景
     */
    public void sendAttachment(String to, String subject, String text, Map<String, Resource> attachments) {
        sendAttachment(List.of(to), subject, text, attachments);
    }

    /**
     * 群发携带附件的邮件,正文为纯文本。
     *
     * @param to          收件人列表
     * @param subject     主题
     * @param text        正文
     * @param attachments 附件名 → 附件内容
     */
    public void sendAttachment(Iterable<String> to, String subject, String text, Map<String, Resource> attachments) {
        send(to, subject, text, attachments);
    }

    private void send(Iterable<String> to, String subject, String text, Map<String, Resource> attachments) {
        String[] recipients = StreamSupport.stream(to.spliterator(), false).toArray(String[]::new);
        if (recipients.length == 0) {
            throw new IllegalArgumentException("to must not be empty");
        }
        String from = resolveFrom();

        mailSender.send(mimeMessage -> {
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, !attachments.isEmpty(), DEFAULT_ENCODING);
            if (from != null) {
                helper.setFrom(from);
            }
            helper.setTo(recipients);
            helper.setSubject(subject);
            helper.setText(text);
            for (Map.Entry<String, Resource> entry : attachments.entrySet()) {
                helper.addAttachment(entry.getKey(), entry.getValue());
            }
        });
    }

    @Nullable
    private String resolveFrom() {
        if (StringUtils.hasText(defaultFrom)) {
            return defaultFrom;
        }
        if (mailSender instanceof JavaMailSenderImpl sender && StringUtils.hasText(sender.getUsername())) {
            return sender.getUsername();
        }
        return null;
    }
}
