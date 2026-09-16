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

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * 注册基于 {@link JavaMailSender} 的 {@link MailTemplate} 门面.
 * <p>
 * 须在 Boot 的 {@link MailSenderAutoConfiguration} 之后执行; 只有存在可用的 {@link JavaMailSender} bean
 * 时才注册,否则优雅跳过。
 */
@AutoConfiguration(after = MailSenderAutoConfiguration.class)
@EnableConfigurationProperties(MailTemplateProperties.class)
@ConditionalOnClass({ MailSenderAutoConfiguration.class, JavaMailSender.class })
public class ButterflyMailAutoConfiguration {

	/**
	 * 注册 {@link MailTemplate} bean.
	 * <p>
	 * 仅当容器中已存在 {@link JavaMailSender} bean、且使用方未自行定义 {@link MailTemplate} 时才创建; 默认发件人取自
	 * {@link MailTemplateProperties} 的 {@code defaultFrom} 配置。
	 * @param mailSender 由 Boot 邮件自动配置提供的邮件发送器
	 * @param properties 邮件相关配置
	 * @return 使用给定发送器与默认发件人构造的门面实例
	 */
	@Bean
	@ConditionalOnBean(JavaMailSender.class)
	@ConditionalOnMissingBean(MailTemplate.class)
	public MailTemplate mailTemplate(JavaMailSender mailSender, MailTemplateProperties properties) {
		return new MailTemplate(mailSender, properties.getDefaultFrom());
	}

}
