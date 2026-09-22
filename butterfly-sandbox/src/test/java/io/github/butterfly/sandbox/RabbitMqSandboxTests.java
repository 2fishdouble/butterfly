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

import io.github.butterfly.rabbitmq.autoconfigure.RabbitMqProperties;
import io.github.butterfly.sandbox.model.Computer;
import io.github.butterfly.sandbox.rabbitmq.RabbitMqComputerConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 校验 RabbitMQ 自动配置对真实 Broker 的收发闭环:拓扑由 {@code butterfly.rabbitmq.*} 声明式建出来、全模块只有一份
 * {@code RabbitTemplate}、监听容器按默认的 manual ack 跑起来,并且 JSON 与发送 ID 都能真正落到消息上.
 * <p>
 * 连接信息(Broker 地址、账号、vhost 与 confirm/return 开关)刻意只写在 {@code application.yml} 里,由
 * {@link #brokerConfigurationComesFromApplicationYml()} 把它与测试常量绑死:yml 被改动时直接失败,而不是静默连到别处。
 */
@SpringBootTest
class RabbitMqSandboxTests {

	/**
	 * 必须与 {@code application.yml} 中 {@code spring.rabbitmq.host} 一致.
	 */
	static final String HOST = "192.168.12.24";

	private static final String EXCHANGE = "butterfly-sandbox-computer";

	private static final String ROUTING_KEY = "butterfly-sandbox.computer";

	private static final String DEAD_LETTER_EXCHANGE = EXCHANGE + ".dlx";

	private static final String DEAD_LETTER_QUEUE = RabbitMqComputerConsumer.QUEUE + ".dlq";

	/**
	 * 只在用例内使用的探针队列:绑到同一个交换机上,用来直接读原始 {@link Message},不与应用内的消费者抢消息.
	 * <p>
	 * 刻意<b>不</b>用 auto-delete:{@code RabbitTemplate#receive} 内部会建一个临时消费者,消费者取消后 RabbitMQ
	 * 会 把 auto-delete 队列一起删掉,第二次 receive 就会 404。
	 */
	private static final String PROBE_QUEUE = "butterfly-sandbox-correlation-probe";

	/**
	 * 探针专用路由键:与应用队列的绑定不同,因此探针消息不会被应用内的消费者收走.
	 */
	private static final String PROBE_ROUTING_KEY = "correlation.probe";

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private RabbitProperties rabbitProperties;

	@Autowired
	private RabbitMqProperties rabbitMqProperties;

	@Autowired
	private AmqpAdmin amqpAdmin;

	@Autowired
	private ApplicationContext applicationContext;

	/**
	 * 每个用例开始前都先把沙箱自带的 computer 监听容器停掉.
	 * <p>
	 * {@code @SpringBootTest} 启动的是整个沙箱应用,{@code RabbitMqComputerConsumer}
	 * 的监听容器一开就会把消息立刻消费 掉,队列里根本"存不住"。所以这里统一停掉它,由用例自己决定什么时候让它开始消费:这样
	 * {@link #sendsComputer()} 发出的消息才会真正留在 {@code butterfly-sandbox-computer-queue}
	 * 里,可以在管理端看到。
	 * <p>
	 * 放在 {@code @BeforeEach} 而不是某个用例里,是为了让用例之间互不影响 —— 消费用例跑完会把容器留在启动状态,下一个 用例开始前又会被这里停掉。
	 */
	@BeforeEach
	void stopComputerListener() {
		RabbitMqComputerConsumer.RECEIVED.clear();
		computerContainer().stop();
	}

	/**
	 * 自检:生效的连接配置必须来自 {@code application.yml},而不是测试内联属性.
	 */
	@Test
	void brokerConfigurationComesFromApplicationYml() {
		assertThat(this.rabbitProperties.getHost()).isEqualTo(HOST);
		assertThat(this.rabbitProperties.getPort()).isEqualTo(5672);
		assertThat(this.rabbitProperties.getVirtualHost()).isEqualTo("/");
		assertThat(this.rabbitProperties.getUsername()).isEqualTo("root");
		assertThat(this.rabbitProperties.getPublisherConfirmType())
			.isEqualTo(CachingConnectionFactory.ConfirmType.CORRELATED);
	}

	/**
	 * 与 Kafka/Redis 不同,RabbitMQ 的模板不分泛型:容器里应当只有 Boot 自动配置的那一个,不按实体类复制.
	 */
	@Test
	void onlyOneRabbitTemplateExists() {
		assertThat(this.applicationContext.getBeansOfType(RabbitTemplate.class)).hasSize(1);
		assertThat(this.applicationContext.containsBean("computerRabbitTemplate")).isFalse();
		assertThat(this.applicationContext.containsBean("timeModuleBeanRabbitTemplate")).isFalse();
	}

	/**
	 * {@code butterfly.rabbitmq.entities.computer.*} 只是配置,真正的交换机/队列/绑定必须已经声明到 Broker 上.
	 */
	@Test
	void topologyIsDeclaredOnRealBroker() {
		RabbitMqProperties.EntityDefinition definition = this.rabbitMqProperties.resolve(Computer.class);

		assertThat(definition.exchange()).isEqualTo(EXCHANGE);
		assertThat(definition.routingKey()).isEqualTo(ROUTING_KEY);
		assertThat(definition.queue()).isEqualTo(RabbitMqComputerConsumer.QUEUE);
		assertThat(definition.exchangeType()).isEqualTo(RabbitMqProperties.ExchangeType.TOPIC);
		assertThat(definition.deadLetterExchange()).isEqualTo(DEAD_LETTER_EXCHANGE);
		assertThat(definition.deadLetterQueue()).isEqualTo(DEAD_LETTER_QUEUE);
		assertThat(definition.deadLetterRoutingKey()).isEqualTo(ROUTING_KEY + ".dead");

		assertThat(this.amqpAdmin.getQueueProperties(definition.queue())).isNotNull();
		assertThat(this.amqpAdmin.getQueueProperties(definition.deadLetterQueue())).isNotNull();
		assertThat(this.amqpAdmin.getQueueInfo(definition.queue())).isNotNull()
			.extracting(QueueInformation::getName)
			.isEqualTo(definition.queue());
	}

	/**
	 * 应用里真正跑起来的那个监听容器必须落在默认的 manual ack 上,与 Kafka 沙箱用例一样是对「默认手动确认」的端到端证据.
	 * <p>
	 * 容器被 {@link #stopComputerListener()} 停掉了,所以这里显式启动它,验完由下一个用例的 {@code @BeforeEach}
	 * 再停掉。
	 */
	@Test
	void computerListenerContainerRunsInManualAckMode() {
		AbstractMessageListenerContainer container = computerContainer();
		container.start();

		assertThat(container.getAcknowledgeMode()).isEqualTo(AcknowledgeMode.MANUAL);
		assertThat(container.isRunning()).isTrue();
	}

	/**
	 * 一次真实的 JSON 收发闭环:用唯一的模板发到该实体类的交换机与路由键,应用内的消费者收下来.
	 * <p>
	 * 消费用的是沙箱应用自己的 {@link RabbitMqComputerConsumer},因此需要先把它的容器启动起来。
	 */
	@Test
	void sendsAndConsumesComputerAsJson() throws Exception {
		computerContainer().start();

		Computer computer = sampleComputer();

		this.rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, computer);

		Computer received = RabbitMqComputerConsumer.RECEIVED.poll(30, TimeUnit.SECONDS);
		assertThat(received).as("消息应当在 30 秒内被消费").isNotNull();
		assertThat(received.getId()).isEqualTo(1L);
		assertThat(received.getName()).isEqualTo("butterfly-sandbox");
		assertThat(received.getCreateTime()).isEqualTo(LocalDateTime.of(2026, 9, 18, 16, 0));
		assertThat(received.getProducts()).hasSize(1);
		assertThat(received.getProducts().getFirst().getName()).isEqualTo("keyboard");
		assertThat(received.getProducts().getFirst().getTotalAmount()).isEqualByComparingTo(new BigDecimal("398.00"));
	}

	/**
	 * 发送时盖到消息属性 {@code correlationId} 上的 ID:传了 {@link CorrelationData} 就用调用方的,没传就现造一个.
	 * <p>
	 * 这条用例同时是「必须配 {@code publisher-confirm-type=correlated}」的证据 —— 换成 {@code simple} 或
	 * {@code none} 时 Spring AMQP 根本不会调用那个处理器,属性会是 null.
	 */
	@Test
	void correlationIdIsStampedOnSentMessages() {
		this.amqpAdmin.declareQueue(QueueBuilder.nonDurable(PROBE_QUEUE).build());
		this.amqpAdmin
			.declareBinding(BindingBuilder.bind(new Queue(PROBE_QUEUE)).to(new TopicExchange(EXCHANGE)).with("#"));
		try {
			this.rabbitTemplate.convertAndSend(EXCHANGE, PROBE_ROUTING_KEY, sampleComputer(),
					new CorrelationData("order-42"));
			Message explicit = this.rabbitTemplate.receive(PROBE_QUEUE, 15_000);
			assertThat(explicit).isNotNull();
			assertThat(explicit.getMessageProperties().getCorrelationId()).isEqualTo("order-42");

			this.rabbitTemplate.convertAndSend(EXCHANGE, PROBE_ROUTING_KEY, sampleComputer());
			Message generated = this.rabbitTemplate.receive(PROBE_QUEUE, 15_000);
			assertThat(generated).isNotNull();
			assertThat(generated.getMessageProperties().getCorrelationId()).as("没传 CorrelationData 时应自动生成")
				.isNotBlank()
				.isNotEqualTo("order-42");
		}
		finally {
			this.amqpAdmin.deleteQueue(PROBE_QUEUE);
		}
	}

	/**
	 * 只发送一条 {@link Computer},不做消费侧断言.
	 * <p>
	 * {@link #stopComputerListener()} 已把应用自己的消费者停掉,所以这条消息会真正留在
	 * {@code butterfly-sandbox-computer-queue} 里,可以在管理端看到它的 JSON 与消息属性。
	 * <p>
	 * 刻意复用 {@link #sampleComputer()}:之前几轮可能已经留下同样内容的消息,内容一致才不会把
	 * {@link #sendsAndConsumesComputerAsJson()} 的断言带偏。
	 */
	@Test
	void sendsComputer() {
		this.rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, sampleComputer());
	}

	/**
	 * 取出应用自带消费者在 computer 队列上的监听容器.
	 * <p>
	 * 监听端点注册表由 Boot 的 RabbitMQ 自动配置在运行时注册,这里按类型从上下文取,而不是 {@code @Autowired} 一个字段: 该 bean
	 * 来自条件化自动配置,IDE 的静态 Bean 模型看不到它,直接注入会报"找不到该类型的 bean"。
	 * @return 监听容器
	 */
	private AbstractMessageListenerContainer computerContainer() {
		return this.applicationContext.getBean(RabbitListenerEndpointRegistry.class)
			.getListenerContainers()
			.stream()
			.map(AbstractMessageListenerContainer.class::cast)
			.filter((candidate) -> Arrays.asList(candidate.getQueueNames()).contains(RabbitMqComputerConsumer.QUEUE))
			.findFirst()
			.orElseThrow(() -> new AssertionError("找不到 computer 队列上的监听容器"));
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
