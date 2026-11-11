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

package io.github.butterfly.file.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;

/**
 * 文件上传/下载接口自动配置:仅在 Servlet Web 应用且 classpath 存在 spring-web 时生效.
 * <p>
 * 之所以单独成一个自动配置类,而不是写成 {@link FileAutoConfiguration} 的嵌套配置类:
 * <ul>
 * <li>嵌套配置类会先于外层配置类被 Spring 解析与注册,类级 {@code @ConditionalOnBean} 拿不到外层声明的
 * {@link FileService},接口就会静默不注册;</li>
 * <li>独立类配合 {@code @AutoConfiguration(after = FileAutoConfiguration.class)} 保证
 * {@link FileService} 的 Bean 定义先注册,类级条件才能匹配;</li>
 * <li>类级 {@link ConditionalOnClass} 用类名判断,缺少 spring-web 时本类不会被加载, 也就不会因为方法签名里的
 * {@code MultipartFile} 抛 {@code NoClassDefFoundError}。</li>
 * </ul>
 */
@SuppressWarnings("BadImport")
@AutoConfiguration(after = FileAutoConfiguration.class)
@ConditionalOnClass(name = "org.springframework.web.multipart.MultipartFile")
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnBean(FileService.class)
public class FileWebAutoConfiguration {

	/**
	 * 注册上传/下载接口,使用方自定义 {@link FileController} 时让位.
	 * @param fileService 文件服务
	 * @return 文件接口控制器
	 */
	@Bean
	@ConditionalOnMissingBean
	FileController fileController(FileService fileService) {
		return new FileController(fileService);
	}

}
