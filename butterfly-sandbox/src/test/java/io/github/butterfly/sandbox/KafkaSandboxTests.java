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

import cn.hutool.core.util.IdUtil;
import io.github.butterfly.kafka.autoconfigure.KafkaTopicProperties;
import io.github.butterfly.sandbox.model.Computer;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 校验 {@code @EnableKafkaTemplates} 注册的 {@code computerKafkaTemplate} 能对真实 Kafka 完成一次 JSON
 * 收发闭环,且主题按配置自动创建.
 * <p>
 * 连接相关配置(Broker 地址 {@value #BROKER}、消费端反序列化器等)通过 {@link SpringBootTest#properties()}
 * 内联声明,由测试自己携带,不读取 {@code application.yml};而主题名/分区数/副本数刻意留给
 * {@code butterfly.kafka.topic.*},由 {@code application.yml} 驱动,以覆盖「配置可选」这条链路。因此测试内
 * {@link #TOPIC} 必须与 yml 中 {@code butterfly.kafka.topic.entities.computer.name} 保持一致,下面的
 * {@code topicIsAutoCreatedFromConfiguration} 会把两者绑定校验,yml 被改动时会直接失败而不是静默跑偏。
 * <p>
 * 这里刻意<b>不</b>声明任何 {@code spring.kafka.consumer.*} /
 * {@code spring.kafka.listener.*}:消费组名、 起始位移、value 反序列化器与 manual ack 全部由
 * {@link io.github.butterfly.kafka.autoconfigure.ButterflyKafkaAutoConfiguration}
 * 提供默认值。本用例能通过, 即证明消费端无需任何 {@code spring.kafka.*} 配置(只保留 Broker 地址)。
 */
@SpringBootTest(properties = { "spring.kafka.bootstrap-servers=" + KafkaSandboxTests.BROKER })
class KafkaSandboxTests {

	/**
	 * 测试自带的 Kafka Broker 地址,不依赖 {@code application.yml}.
	 */
	static final String BROKER = "192.168.12.29:9092";

	/**
	 * 必须与 {@code application.yml} 中 {@code butterfly.kafka.topic.entities.computer.name}
	 * 一致.
	 */
	private static final String TOPIC = "butterfly-sandbox-computer";

	static final String GROUP_ID = "butterfly-sandbox-test";

	@Autowired
	private KafkaTemplate<String, Computer> computerKafkaTemplate;

	@Autowired
	private KafkaProperties kafkaProperties;

	@Autowired
	private KafkaTopicProperties topicProperties;

	@Autowired
	private KafkaAdmin kafkaAdmin;

	@Autowired
	@Qualifier("computerNewTopic")
	private NewTopic computerNewTopic;

	@Autowired
	private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

	@BeforeEach
	void clearReceived() {
		ComputerConsumer.RECEIVED.clear();
	}

	/**
	 * 自检:生效的 bootstrap-servers 必须来自本类的内联属性,而非 {@code application.yml}.
	 */
	@Test
	void brokerAddressComesFromTestProperties() {
		assertThat(this.kafkaProperties.getBootstrapServers()).containsExactly(BROKER);
	}

	/**
	 * 模板只把序列化器类型写进生产者配置,实例由 Kafka 客户端创建;真正的 JSON 收发闭环由下面的用例验证。
	 */
	@Test
	void computerTemplateConfiguresJacksonJsonSerializer() {
		assertThat(this.computerKafkaTemplate.getProducerFactory().getConfigurationProperties())
			.containsEntry(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
	}

	/**
	 * 主题无需手写 {@code NewTopics} Bean:{@code @EnableKafkaTemplates(Computer.class)}
	 * 已按配置自动注册 {@code computerNewTopic},并由 {@code KafkaAdmin} 在启动时把主题真正建到 Broker 上。
	 */
	@Test
	void topicIsAutoCreatedFromConfiguration() {
		KafkaTopicProperties.TopicDefinition expected = this.topicProperties.resolve(Computer.class);

		assertThat(expected.name()).isEqualTo(TOPIC);
		assertThat(this.computerNewTopic.name()).isEqualTo(expected.name());
		assertThat(this.computerNewTopic.numPartitions()).isEqualTo(expected.partitions());
		assertThat(this.computerNewTopic.replicationFactor()).isEqualTo((short) expected.replicas());

		TopicDescription description = this.kafkaAdmin.describeTopics(expected.name()).get(expected.name());
		assertThat(description).isNotNull();
		assertThat(description.partitions()).hasSize(expected.partitions());
	}

	/**
	 * 实际跑起来的监听容器必须落在框架默认的 manual ack 上——这是「默认使用 manual 监听」的端到端证据。
	 */
	@Test
	void listenerContainerRunsInManualAckMode() {
		Collection<MessageListenerContainer> containers = this.kafkaListenerEndpointRegistry.getListenerContainers();

		assertThat(containers).isNotEmpty();
		assertThat(containers).allSatisfy((container) -> assertThat(container.getContainerProperties().getAckMode())
			.isEqualTo(ContainerProperties.AckMode.MANUAL));
	}

	@Test
	void sendsAndConsumesComputerAsJson() throws Exception {
		Computer computer = sampleComputer();

		SendResult<String, Computer> result = this.computerKafkaTemplate.send(TOPIC, "computer-1", computer)
			.get(15, TimeUnit.SECONDS);

		assertThat(result.getRecordMetadata().topic()).isEqualTo(TOPIC);
		assertThat(result.getRecordMetadata().hasOffset()).isTrue();

		Computer received = ComputerConsumer.RECEIVED.poll(30, TimeUnit.SECONDS);
		assertThat(received).isNotNull();
		assertThat(received.getId()).isEqualTo(1L);
		assertThat(received.getName()).isEqualTo("butterfly-sandbox");
		assertThat(received.getCreateTime()).isEqualTo(LocalDateTime.of(2026, 9, 17, 16, 0));
		assertThat(received.getProducts()).hasSize(1);
		assertThat(received.getProducts().getFirst().getName()).isEqualTo("keyboard");
		assertThat(received.getProducts().getFirst().getTotalAmount()).isEqualByComparingTo(new BigDecimal("398.00"));
	}

	@Test
	void sendsComputer() throws Exception {
		Computer computer = randomComputer();

		SendResult<String, Computer> result = this.computerKafkaTemplate.send(TOPIC, "computer-1", computer)
			.get(15, TimeUnit.SECONDS);

		assertThat(result.getRecordMetadata().topic()).isEqualTo(TOPIC);
		assertThat(result.getRecordMetadata().hasOffset()).isTrue();
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
		computer.setCreateTime(LocalDateTime.of(2026, 9, 17, 16, 0));
		computer.setProducts(List.of(product));
		return computer;
	}

	private Computer randomComputer() {
		Computer.Product product = new Computer.Product();
		product.setId(IdUtil.getSnowflakeNextId());
		product.setName("keyboard");
		product.setDescription("mechanical keyboard");
		product.setPrice(new BigDecimal("199.00"));
		product.setQuantity(2);
		product.setTotalAmount(new BigDecimal("398.00"));

		Computer computer = new Computer();
		computer.setId(IdUtil.getSnowflakeNextId());
		computer.setName("butterfly-sandbox");
		computer.setCreateTime(LocalDateTime.now());
		computer.setProducts(List.of(product));
		return computer;
	}

	/**
	 * 注册消费端:嵌套的 {@link TestConfiguration} 会被 {@code @SpringBootTest} 自动纳入上下文.
	 */
	@TestConfiguration(proxyBeanMethods = false)
	static class ConsumerConfiguration {

		@Bean
		ComputerConsumer computerConsumer() {
			return new ComputerConsumer();
		}

	}

	/**
	 * 把收到的 {@link Computer} 投进静态队列,供测试线程断言.
	 */
	static class ComputerConsumer {

		static final BlockingQueue<Computer> RECEIVED = new LinkedBlockingQueue<>();

		@KafkaListener(topics = TOPIC, groupId = GROUP_ID)
		void onComputer(Computer computer, Acknowledgment acknowledgment) {
			RECEIVED.add(computer);
			acknowledgment.acknowledge();
		}

	}

}
