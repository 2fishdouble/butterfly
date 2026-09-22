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

import io.github.butterfly.rocketmq.autoconfigure.RocketMqProperties;
import io.github.butterfly.rocketmq.autoconfigure.RocketMqSender;
import io.github.butterfly.rocketmq.autoconfigure.RocketMqTopicAdmin;
import io.github.butterfly.sandbox.model.Computer;
import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.MessageQueue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 校验 RocketMQ 自动配置对真实 Broker 的闭环:主题名/消费组/死信主题由 {@code butterfly.rocketmq.*} 解析出来、 主主题由老版
 * remoting 客户端(带 ACL 签名)真正建到 NameServer 上、发送走 v5 gRPC 代理。
 * <p>
 * 连接信息(两套地址与 ACL 凭据)刻意只写在 {@code application.yml} 里,由
 * {@link #brokerConfigurationComesFromApplicationYml()} 把它与测试常量绑死:yml 被改动时直接失败,
 * 而不是静默连到别处。
 */
@SpringBootTest
class RocketMqSandboxTests {

	/**
	 * 必须与 {@code application.yml} 中 {@code rocketmq.name-server} 一致.
	 */
	static final String NAME_SERVER = "192.168.12.29:9876";

	/**
	 * 必须与 {@code application.yml} 中 {@code rocketmq.producer.endpoints} 一致.
	 */
	static final String ENDPOINTS = "192.168.12.29:8081";

	/**
	 * 必须与 {@code application.yml} 中 {@code butterfly.rocketmq.entities.computer.topic}
	 * 一致.
	 */
	static final String TOPIC = "butterfly-sandbox-computer";

	/**
	 * 必须与 {@code application.yml} 中 {@code rocketmq.producer.access-key} 一致.
	 */
	static final String ACCESS_KEY = "super_admin";

	/**
	 * 必须与 {@code application.yml} 中 {@code rocketmq.producer.secret-key} 一致.
	 */
	static final String SECRET_KEY = "Aa123456";

	/**
	 * 建主题时申请的队列数,与 {@code butterfly.rocketmq.queue-count} 的默认值一致.
	 */
	private static final int QUEUE_COUNT = 4;

	@Autowired
	private RocketMqProperties rocketMqProperties;

	@Autowired
	private RocketMqSender computerRocketMqSender;

	@Autowired
	private RocketMqTopicAdmin rocketMqTopicAdmin;

	@Autowired
	private Environment environment;

	/**
	 * 自检:生效的连接配置必须来自 {@code application.yml},而不是测试内联属性.
	 */
	@Test
	void brokerConfigurationComesFromApplicationYml() {
		assertThat(this.environment.getProperty("rocketmq.name-server")).isEqualTo(NAME_SERVER);
		assertThat(this.environment.getProperty("rocketmq.producer.endpoints")).isEqualTo(ENDPOINTS);
		assertThat(this.environment.getProperty("rocketmq.producer.access-key")).isEqualTo(ACCESS_KEY);
		assertThat(this.environment.getProperty("rocketmq.producer.secret-key")).isEqualTo(SECRET_KEY);
		// 明文 gRPC 的代理上必须关掉 TLS,否则发送会卡在握手
		assertThat(this.environment.getProperty("rocketmq.producer.ssl-enabled")).isEqualTo("false");
	}

	/**
	 * 一个实体对应一个主题、一个消费组,死信与重试主题按 RocketMQ 规则从消费组派生.
	 */
	@Test
	void resolvesTopicConsumerGroupAndDeadLetterNames() {
		RocketMqProperties.TopicDefinition definition = this.rocketMqProperties.resolve(Computer.class);

		assertThat(definition.topic()).isEqualTo(TOPIC);
		assertThat(definition.consumerGroup()).isEqualTo("computer");
		assertThat(definition.deadLetterTopic()).isEqualTo("%DLQ%computer");
		assertThat(definition.retryTopic()).isEqualTo("%RETRY%computer");
		assertThat(definition.queueCount()).isEqualTo(QUEUE_COUNT);
		assertThat(definition.createTopic()).isTrue();
	}

	/**
	 * 沙箱引入了 {@code rocketmq-client} 与 {@code rocketmq-acl} 并配了
	 * NameServer,所以拿到的必须是真正建主题的实现, 而不是只记日志的兜底实现.
	 */
	@Test
	void usesClassicTopicAdminInsteadOfLoggingFallback() {
		assertThat(this.rocketMqTopicAdmin.getClass().getSimpleName()).isEqualTo("ClassicRocketMqTopicAdmin");
	}

	/**
	 * 启动时的初始化器已经用带 ACL 签名的管理客户端建过一次主题;这里再建一次必须幂等.
	 */
	@Test
	void creatingTheSameTopicTwiceIsIdempotent() {
		assertThatCode(() -> this.rocketMqTopicAdmin.createTopic(TOPIC, QUEUE_COUNT)).doesNotThrowAnyException();
	}

	/**
	 * 主题必须真的存在:用老版客户端(同样带 ACL 签名)回读路由.
	 * <p>
	 * 这是"自动创建 topic"最直接的证据 —— 路由查得到,说明主题在 Broker 上确实有队列,而不是只有一段配置.
	 */
	@Test
	void topicRouteExistsOnRealBroker() throws Exception {
		DefaultMQProducer verifier = new DefaultMQProducer("butterfly-sandbox-topic-verifier",
				new AclClientRPCHook(new SessionCredentials(ACCESS_KEY, SECRET_KEY)));
		verifier.setNamesrvAddr(NAME_SERVER);
		verifier.start();
		try {
			List<MessageQueue> queues = verifier.fetchPublishMessageQueues(TOPIC);

			assertThat(queues).isNotEmpty();
			assertThat(queues).allSatisfy((queue) -> assertThat(queue.getTopic()).isEqualTo(TOPIC));
		}
		finally {
			verifier.shutdown();
		}
	}

	/**
	 * 真正通过 v5 gRPC 代理(带 ACL)发出消息:拿到回执即证明代理连得上、签名被接受、主题可写.
	 */
	@Test
	void sendsComputerThroughRealProxy() {
		SendReceipt receipt = this.computerRocketMqSender.send(sampleComputer());

		assertThat(receipt).isNotNull();
		assertThat(receipt.getMessageId()).isNotNull();
		assertThat(receipt.getMessageId().toString()).isNotBlank();
	}

	private Computer sampleComputer() {
		Computer.Product product = new Computer.Product();
		product.setId(10L);
		product.setName("keyboard");
		product.setDescription("mechanical keyboard");
		product.setPrice(new BigDecimal("199.00"));
		product.setQuantity(2);
		product.setTotalAmount(new BigDecimal("398.00"));

		Computer computer = new Computer();
		computer.setId(1L);
		computer.setName("butterfly-sandbox");
		computer.setCreateTime(LocalDateTime.of(2026, 9, 18, 16, 0));
		computer.setProducts(List.of(product));
		return computer;
	}

}
