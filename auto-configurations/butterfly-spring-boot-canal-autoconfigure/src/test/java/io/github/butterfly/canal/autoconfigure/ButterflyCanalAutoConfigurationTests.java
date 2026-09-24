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

package io.github.butterfly.canal.autoconfigure;

import lombok.Data;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 校验自动配置的装配结果:属性绑定与默认值、三种模式的消息来源、同步与异步消费端、处理器注册,以及各项 {@code @ConditionalOnMissingBean}
 * 退让规则。全程不建立任何网络连接。
 */
class ButterflyCanalAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ButterflyCanalAutoConfiguration.class))
		.withPropertyValues("butterfly.canal.auto-startup=false");

	@Test
	void autoConfigurationImportsFileDeclaresThisConfiguration() throws IOException {
		ClassPathResource resource = new ClassPathResource(
				"META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");

		assertThat(resource.getContentAsString(StandardCharsets.UTF_8))
			.contains(ButterflyCanalAutoConfiguration.class.getName());
	}

	@Test
	void bindsDefaultProperties() {
		this.contextRunner.run((context) -> {
			CanalProperties properties = context.getBean(CanalProperties.class);

			assertThat(properties.isEnabled()).isTrue();
			assertThat(properties.getConsumerType()).isEqualTo(CanalConsumerType.SYNC);
			assertThat(properties.getHost()).isEqualTo("127.0.0.1");
			assertThat(properties.getPort()).isEqualTo(11111);
			assertThat(properties.getBatchSize()).isEqualTo(1000);
			assertThat(properties.getTimeout()).isEqualTo(Duration.ofSeconds(1));
			assertThat(properties.getErrorBackOff()).isEqualTo(Duration.ofSeconds(1));
			assertThat(properties.getFilter()).isEqualTo(".*\\..*");
			assertThat(properties.getDestination()).isNull();
			assertThat(properties.getSoTimeout()).isNull();
			assertThat(properties.getAsync().getThreadNamePrefix()).isEqualTo("butterfly-canal-");
		});
	}

	@Test
	void autoStartupDefaultsToTrueAndIsConfigurable() {
		assertThat(new CanalProperties().isAutoStartup()).isTrue();

		this.contextRunner.withPropertyValues("butterfly.canal.auto-startup=false")
			.run((context) -> assertThat(context.getBean(CanalProperties.class).isAutoStartup()).isFalse());
	}

	@Test
	void createsConnectorSourceAndSyncConsumerByDefault() {
		this.contextRunner.run((context) -> {
			assertThat(context).hasSingleBean(CanalConnectorMessageSource.class);
			// 消费端启动前不创建连接器,因此应用启动不会受 canal 可用性影响
			assertThat(context.getBean(CanalConnectorMessageSource.class).connector()).isNull();
			assertThat(context).hasSingleBean(SyncCanalEventConsumer.class);
			assertThat(context).doesNotHaveBean(AsyncCanalEventConsumer.class);
			assertThat(context.getBean(CanalEventConsumer.class).isRunning()).isFalse();
		});
	}

	@Test
	void createsAsyncConsumerWithItsOwnExecutor() {
		this.contextRunner
			.withPropertyValues("butterfly.canal.consumer-type=async", "butterfly.canal.async.core-pool-size=3")
			.run((context) -> {
				assertThat(context).hasSingleBean(AsyncCanalEventConsumer.class);
				assertThat(context).doesNotHaveBean(SyncCanalEventConsumer.class);

				ThreadPoolTaskExecutor executor = context.getBean("butterflyCanalAsyncExecutor",
						ThreadPoolTaskExecutor.class);
				assertThat(executor.getCorePoolSize()).isEqualTo(3);
				// 线程池已由 Spring 初始化(未初始化时 getThreadPoolExecutor 会抛异常)
				assertThat(executor.getThreadPoolExecutor()).isNotNull();
			});
	}

	@Test
	void disablesEverythingWhenEnabledIsFalse() {
		this.contextRunner.withPropertyValues("butterfly.canal.enabled=false").run((context) -> {
			assertThat(context).doesNotHaveBean(CanalProperties.class);
			assertThat(context).doesNotHaveBean(CanalMessageSource.class);
			assertThat(context).doesNotHaveBean(CanalEventDispatcher.class);
			assertThat(context).doesNotHaveBean(CanalEventConsumer.class);
		});
	}

	@Test
	void registersHandlersFromTheApplicationContext() {
		this.contextRunner.withUserConfiguration(HandlerConfiguration.class).run((context) -> {
			CanalEventDispatcher dispatcher = context.getBean(CanalEventDispatcher.class);
			// 泛型处理器覆盖 INSERT、UPDATE、DELETE 三个事件,注解方法只声明了 INSERT
			assertThat(dispatcher.handlerCount()).isEqualTo(4);

			dispatcher.dispatch(new CanalEvent("example", "butterfly", "computer", CanalEventType.INSERT,
					Map.of("id", "1"), null, null, Instant.EPOCH));

			assertThat(context.getBean(Recorder.class).handled()).containsExactlyInAnyOrder("row-insert:1",
					"listener-insert:1");
		});
	}

	@Test
	void userMessageSourceTakesPrecedence() {
		this.contextRunner.withUserConfiguration(UserMessageSourceConfiguration.class).run((context) -> {
			assertThat(context).hasSingleBean(CanalMessageSource.class);
			assertThat(context.getBean(CanalMessageSource.class)).isInstanceOf(TestCanalMessageSource.class);
		});
	}

	@Test
	void userRowMapperTakesPrecedence() {
		this.contextRunner.withUserConfiguration(UserRowMapperConfiguration.class)
			.run((context) -> assertThat(context.getBean(CanalRowMapper.class)).isInstanceOf(TestCanalRowMapper.class));
	}

	/**
	 * 事件实体:表名 {@code computer}.
	 */
	@Data
	static class Computer {

		private Long id;

	}

	/**
	 * 记录处理器收到的内容.
	 */
	static class Recorder {

		private final List<String> handled = new CopyOnWriteArrayList<>();

		void add(String value) {
			this.handled.add(value);
		}

		List<String> handled() {
			return this.handled;
		}

	}

	static class ComputerRowHandler implements CanalRowHandler<Computer> {

		private final Recorder recorder;

		ComputerRowHandler(Recorder recorder) {
			this.recorder = recorder;
		}

		@Override
		public void insert(Computer row) {
			this.recorder.add("row-insert:" + row.getId());
		}

	}

	static class ComputerListener {

		private final Recorder recorder;

		ComputerListener(Recorder recorder) {
			this.recorder = recorder;
		}

		@CanalListener(table = "computer", events = CanalEventType.INSERT)
		public void onInsert(Computer computer) {
			this.recorder.add("listener-insert:" + computer.getId());
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class HandlerConfiguration {

		@Bean
		Recorder recorder() {
			return new Recorder();
		}

		@Bean
		ComputerRowHandler computerRowHandler(Recorder recorder) {
			return new ComputerRowHandler(recorder);
		}

		@Bean
		ComputerListener computerListener(Recorder recorder) {
			return new ComputerListener(recorder);
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class UserMessageSourceConfiguration {

		@Bean
		CanalMessageSource myCanalMessageSource() {
			return new TestCanalMessageSource();
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class UserRowMapperConfiguration {

		@Bean
		CanalRowMapper myCanalRowMapper() {
			return new TestCanalRowMapper();
		}

	}

	static class TestCanalRowMapper implements CanalRowMapper {

		@Override
		public <T> T map(Map<String, String> row, Class<T> type) {
			throw new UnsupportedOperationException("test row mapper is not supposed to map rows");
		}

	}

}
