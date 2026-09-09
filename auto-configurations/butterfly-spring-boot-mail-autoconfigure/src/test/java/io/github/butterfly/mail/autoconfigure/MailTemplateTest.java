package io.github.butterfly.mail.autoconfigure;

import jakarta.mail.Address;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessagePreparator;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 校验 {@link MailTemplate} 组装出的 MimeMessage(收件人/发件人/主题/正文/附件),全程不触网。
 */
class MailTemplateTest {

    private static final String DEFAULT_FROM = "sender@example.com";

    private final JavaMailSender mailSender = mock(JavaMailSender.class);

    @Test
    void sendTextBuildsPlainTextMessage() throws Exception {
        MailTemplate template = new MailTemplate(mailSender, DEFAULT_FROM);
        template.sendText("to@example.com", "subject", "hello butterfly");

        MimeMessage mime = prepare(capturedPreparator(mailSender));

        assertThat(mime.getSubject()).isEqualTo("subject");
        assertThat(mime.getFrom()[0].toString()).isEqualTo(DEFAULT_FROM);
        assertThat(mime.getAllRecipients()).extracting(Address::toString)
                .containsExactly("to@example.com");
        assertThat(mime.getContentType()).startsWith("text/plain");
        assertThat(mime.getContent()).isEqualTo("hello butterfly");
    }

    @Test
    void sendAttachmentAttachesFilesAsMultipart() throws Exception {
        MailTemplate template = new MailTemplate(mailSender, DEFAULT_FROM);
        byte[] payload = "attachment-body".getBytes(StandardCharsets.UTF_8);
        template.sendAttachment("to@example.com", "attachment subject", "body text",
                Map.of("report.txt", new ByteArrayResource(payload)));

        MimeMessage mime = prepare(capturedPreparator(mailSender));

        assertThat(mime.getContentType()).startsWith("multipart/mixed");
        Multipart multipart = (Multipart) mime.getContent();
        List<Part> parts = new ArrayList<>();
        collect(parts, multipart);

        Part filePart = parts.stream()
                .filter(part -> "report.txt".equals(fileName(part)))
                .findFirst()
                .orElseThrow();
        assertThat(filePart.getDisposition()).isEqualTo(Part.ATTACHMENT);
        try (InputStream in = filePart.getInputStream()) {
            assertThat(in.readAllBytes()).isEqualTo(payload);
        }
        assertThat(parts).extracting(this::partText).contains("body text");
    }

    private static String fileName(Part part) {
        try {
            return part.getFileName();
        } catch (jakarta.mail.MessagingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String partText(Part part) {
        try {
            return part.getContent() instanceof String text ? text : null;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void collect(List<Part> parts, Multipart multipart) throws Exception {
        for (int i = 0; i < multipart.getCount(); i++) {
            Part part = multipart.getBodyPart(i);
            parts.add(part);
            if (part.getContent() instanceof Multipart nested) {
                collect(parts, nested);
            }
        }
    }

    @Test
    void fallsBackToSenderUsernameWhenNoDefaultFrom() throws Exception {
        JavaMailSenderImpl sender = mock(JavaMailSenderImpl.class);
        when(sender.getUsername()).thenReturn("no-reply@example.com");
        MailTemplate template = new MailTemplate(sender, null);

        template.sendText(List.of("a@example.com", "b@example.com"), "subject", "text");

        MimeMessage mime = prepare(capturedPreparator(sender));
        assertThat(mime.getFrom()[0].toString()).isEqualTo("no-reply@example.com");
        assertThat(mime.getAllRecipients()).extracting(Address::toString)
                .containsExactly("a@example.com", "b@example.com");
    }

    @Test
    void rejectsEmptyRecipients() {
        MailTemplate template = new MailTemplate(mailSender, DEFAULT_FROM);
        assertThatThrownBy(() -> template.sendText(List.of(), "subject", "text"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static MimeMessagePreparator capturedPreparator(JavaMailSender sender) {
        ArgumentCaptor<MimeMessagePreparator> captor = ArgumentCaptor.forClass(MimeMessagePreparator.class);
        verify(sender).send(captor.capture());
        return captor.getValue();
    }

    private static MimeMessage prepare(MimeMessagePreparator preparator) throws Exception {
        MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()));
        preparator.prepare(mime);
        mime.saveChanges();
        return mime;
    }
}
