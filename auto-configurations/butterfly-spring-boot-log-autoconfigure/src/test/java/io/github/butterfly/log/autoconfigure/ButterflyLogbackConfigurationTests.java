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

package io.github.butterfly.log.autoconfigure;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.status.Status;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 日志配置文件的测试.
 * <p>
 * 本模块没有 Java 源码,能出问题的只有 XML:标签写错、include 的路径不对、属性没解析出来。 所以这里用 logback 自己的
 * {@link JoranConfigurator} 直接加载配置文件,断言 appender、 级别和日志文件路径,并把 logback 记录的 ERROR 状态当作失败。
 */
class ButterflyLogbackConfigurationTests {

	/**
	 * 日志输出目录放在 target/ 下:logback 会真的创建并打开日志文件,而 {@code DefaultAppender_inner} 只被定义、没有被
	 * root 引用时不会随 {@code context.stop()} 关闭,文件句柄会留到 JVM 退出。用 {@code @TempDir} 时 JUnit
	 * 的清理会因此失败(Windows 上文件被占用)。
	 */
	private static final Path LOG_DIR = Path.of("target", "test-logs");

	@TempDir
	Path workDir;

	private String originalLogPath;

	private String originalLogFile;

	@BeforeEach
	void setUp() {
		this.originalLogPath = System.getProperty("LOG_PATH");
		this.originalLogFile = System.getProperty("LOG_FILE");
		// 让默认配置里的日志文件落到 target/ 下,避免在模块目录里产生 logs/
		System.clearProperty("LOG_FILE");
		System.setProperty("LOG_PATH", LOG_DIR.toString());
	}

	@AfterEach
	void tearDown() {
		resetSystemProperty("LOG_FILE", this.originalLogFile);
		resetSystemProperty("LOG_PATH", this.originalLogPath);
	}

	@Test
	void defaultConfigurationProvidesConsoleAndRollingFileAppenders() throws JoranException {
		LoggerContext context = configure(classpathUrl("logback.xml"));
		try {
			Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
			assertThat(root.getLevel()).isEqualTo(Level.INFO);
			assertThat(root.getAppender("STDOUT")).isInstanceOf(ConsoleAppender.class);

			Appender<ILoggingEvent> async = root.getAppender("DefaultAppender");
			assertThat(async).isInstanceOf(AsyncAppender.class);
			Appender<ILoggingEvent> file = ((AsyncAppender) async).getAppender("DefaultAppender_inner");
			assertThat(file).isInstanceOf(RollingFileAppender.class);
			assertThat(Path.of(((RollingFileAppender<?>) file).getFile()))
				.isEqualTo(LOG_DIR.resolve("application").resolve("application.log"));

			assertThat(context.getLogger("io.netty").getLevel()).isEqualTo(Level.WARN);
		}
		finally {
			context.stop();
		}
	}

	@Test
	void defaultsFragmentCanBeIncludedWithoutInheritingTheRootLogger() throws Exception {
		Path serviceConfig = this.workDir.resolve("service-logback.xml");
		Files.writeString(serviceConfig, """
				<configuration>
					<include resource="butterfly/logback/butterfly-logback-defaults.xml"/>
					<appender name="CUSTOM" class="ch.qos.logback.core.ConsoleAppender">
						<encoder>
							<pattern>%msg%n</pattern>
						</encoder>
					</appender>
					<root level="DEBUG">
						<appender-ref ref="CUSTOM"/>
					</root>
				</configuration>
				""", StandardCharsets.UTF_8);

		LoggerContext context = configure(serviceConfig.toUri().toURL());
		try {
			Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
			// 服务自己的 root 说了算:只有 CUSTOM,公共片段里的 STDOUT/DefaultAppender 不该被挂上去
			assertThat(root.getLevel()).isEqualTo(Level.DEBUG);
			assertThat(root.getAppender("CUSTOM")).isNotNull();
			assertThat(root.getAppender("STDOUT")).isNull();
			assertThat(root.getAppender("DefaultAppender")).isNull();
		}
		finally {
			context.stop();
		}
	}

	@Test
	void baseFragmentReproducesTheDefaultRootLogger() throws Exception {
		Path serviceConfig = this.workDir.resolve("service-logback-base.xml");
		Files.writeString(serviceConfig, """
				<configuration>
					<include resource="butterfly/logback/butterfly-logback-base.xml"/>
				</configuration>
				""", StandardCharsets.UTF_8);

		LoggerContext context = configure(serviceConfig.toUri().toURL());
		try {
			Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
			assertThat(root.getLevel()).isEqualTo(Level.INFO);
			assertThat(root.getAppender("STDOUT")).isNotNull();
			assertThat(root.getAppender("DefaultAppender")).isNotNull();
		}
		finally {
			context.stop();
		}
	}

	private static LoggerContext configure(URL configUrl) throws JoranException {
		LoggerContext context = new LoggerContext();
		context.setName("butterfly-log-tests");
		JoranConfigurator configurator = new JoranConfigurator();
		configurator.setContext(context);
		configurator.doConfigure(configUrl);
		assertNoConfigurationErrors(context);
		return context;
	}

	private static URL classpathUrl(String resource) {
		URL url = Thread.currentThread().getContextClassLoader().getResource(resource);
		assertThat(url).as("classpath resource %s", resource).isNotNull();
		return url;
	}

	/**
	 * logback 遇到错误(包括 include 找不到资源)不会抛异常,只把状态记在 StatusManager 里,
	 * 所以必须显式检查,否则配置写错时测试会静默通过。
	 */
	private static void assertNoConfigurationErrors(LoggerContext context) {
		List<Status> errors = context.getStatusManager()
			.getCopyOfStatusList()
			.stream()
			.filter((status) -> status.getLevel() == Status.ERROR)
			.toList();
		assertThat(errors).as("logback configuration errors: %s", errors).isEmpty();
	}

	private static void resetSystemProperty(String name, String originalValue) {
		System.setProperty(name, originalValue);
	}

}
