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

package io.github.butterfly.sandbox;

import io.github.butterfly.canal.autoconfigure.CanalConnectorMessageSource;
import io.github.butterfly.canal.autoconfigure.CanalConsumerType;
import io.github.butterfly.canal.autoconfigure.CanalEvent;
import io.github.butterfly.canal.autoconfigure.CanalEventDispatcher;
import io.github.butterfly.canal.autoconfigure.CanalEventType;
import io.github.butterfly.canal.autoconfigure.CanalMessageSource;
import io.github.butterfly.canal.autoconfigure.CanalProperties;
import io.github.butterfly.sandbox.canal.ComputerListener;
import io.github.butterfly.sandbox.canal.ComputerRowHandler;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * canal 沙箱的自检:配置来自 {@code application.yml}、启动阶段不连 canal、两种写法的处理器都能收到三类事件.
 * <p>
 * 这里刻意用 {@code auto-startup=false} 把消费端关掉:沙箱测试只验证装配与分发链路,不需要真的有一个 canal server 在跑;事件通过
 * {@link CanalEventDispatcher} 直接投递,因此用例完全自包含。要端到端验证,请把本项改为 {@code true} 并把
 * {@code application.yml} 里的地址指向可用的 canal server。
 */
@SpringBootTest(properties = { "butterfly.canal.auto-startup=false" })
class CanalSandboxTests {

	@Autowired
	private CanalProperties properties;

	@Autowired
	private CanalMessageSource messageSource;

	@Autowired
	private CanalEventDispatcher dispatcher;

	@BeforeEach
	void clearReceived() {
		ComputerRowHandler.RECEIVED.clear();
		ComputerListener.RECEIVED.clear();
	}

	/**
	 * {@code destination} 显式配成了 canal 自带的 {@code example},而不是回落到
	 * {@code spring.application.name}.
	 */
	@Test
	void bindsCanalConfigurationFromApplicationYaml() {
		assertThat(this.properties.getDestination()).isEqualTo("example");
		assertThat(this.properties.getHost()).isEqualTo("127.0.0.1");
		assertThat(this.properties.getPort()).isEqualTo(11111);
		assertThat(this.properties.getFilter()).isEqualTo(".*\\..*");
		assertThat(this.properties.getConsumerType()).isEqualTo(CanalConsumerType.SYNC);
	}

	/**
	 * {@code auto-startup=false} 时不拉取,连接器也要等消费端启动、调用 {@code connect()} 时才创建。
	 */
	@Test
	void doesNotConnectAtStartup() {
		assertThat(this.messageSource).isInstanceOf(CanalConnectorMessageSource.class);
		assertThat(((CanalConnectorMessageSource) this.messageSource).connector()).isNull();
		// 泛型驱动 3 个事件 + 注解驱动 3 个事件
		assertThat(this.dispatcher.handlerCount()).isEqualTo(6);
	}

	@Test
	void dispatchesEveryEventTypeToBothHandlerStyles() {
		this.dispatcher.dispatch(insert());
		this.dispatcher.dispatch(update());
		this.dispatcher.dispatch(delete());

		assertThat(ComputerRowHandler.RECEIVED).containsExactly("insert:1", "update:1:pc-pro", "delete:1");
		assertThat(ComputerListener.RECEIVED).containsExactly("insert:1", "update:1:pc-pro", "delete:1");
	}

	@Test
	void ignoresEventsOfOtherTables() {
		this.dispatcher.dispatch(new CanalEvent("example", "butterfly", "monitor", CanalEventType.INSERT,
				Map.of("id", "1"), null, null, Instant.EPOCH));

		assertThat(ComputerRowHandler.RECEIVED).isEmpty();
		assertThat(ComputerListener.RECEIVED).isEmpty();
	}

	private static CanalEvent insert() {
		return event(CanalEventType.INSERT, Map.of("id", "1", "name", "pc"), null);
	}

	private static CanalEvent update() {
		return event(CanalEventType.UPDATE, Map.of("id", "1", "name", "pc-pro"), Map.of("name", "pc"));
	}

	private static CanalEvent delete() {
		return event(CanalEventType.DELETE, Map.of("id", "1", "name", "pc"), null);
	}

	private static CanalEvent event(CanalEventType eventType, Map<String, String> row,
			@Nullable Map<String, String> before) {
		return new CanalEvent("example", "butterfly", "computer", eventType, row, before, null, Instant.EPOCH);
	}

}
