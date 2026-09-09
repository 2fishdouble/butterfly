package io.github.butterfly.mail.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ButterflyMailAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ButterflyMailAutoConfiguration.class));

    @Test
    void javaMailSenderPresentRegistersMailTemplate() {
        this.contextRunner
                .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
                .run(context -> assertThat(context).hasSingleBean(MailTemplate.class));
    }

    @Test
    void noJavaMailSenderSkipsMailTemplate() {
        this.contextRunner.run(context -> assertThat(context).doesNotHaveBean(MailTemplate.class));
    }

    @Test
    void registersAfterBootMailSenderAutoConfiguration() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        MailSenderAutoConfiguration.class,
                        ButterflyMailAutoConfiguration.class))
                .withPropertyValues("spring.mail.host=smtp.example.com")
                .run(context -> assertThat(context).hasSingleBean(MailTemplate.class));
    }
}
