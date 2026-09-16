/*
 * Copyright 2012-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.butterfly.mail.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ButterflyMailAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ButterflyMailAutoConfiguration.class));

	@Test
	void javaMailSenderPresentRegistersMailTemplate() {
		this.contextRunner.withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
			.run((context) -> assertThat(context).hasSingleBean(MailTemplate.class));
	}

	@Test
	void noJavaMailSenderSkipsMailTemplate() {
		this.contextRunner.run((context) -> assertThat(context).doesNotHaveBean(MailTemplate.class));
	}

	@Test
	void registersAfterBootMailSenderAutoConfiguration() {
		new ApplicationContextRunner()
			.withConfiguration(
					AutoConfigurations.of(MailSenderAutoConfiguration.class, ButterflyMailAutoConfiguration.class))
			.withPropertyValues("spring.mail.host=smtp.example.com")
			.run((context) -> assertThat(context).hasSingleBean(MailTemplate.class));
	}

}
