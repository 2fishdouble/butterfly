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

package io.github.butterfly.kafka.autoconfigure;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.retrytopic.RetryTopicConfiguration;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 校验 {@link EnableKafkaTemplates} 按实体类注册专用模板与主题、命名规则与默认值,全程不触网.
 */
class EnableKafkaTemplatesTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(ProducerFactoryConfiguration.class);

	@Test
	void registersTemplatePerEntityClass() {
		this.contextRunner.withUserConfiguration(SampleConfiguration.class).run((context) -> {
			assertThat(context).hasBean("sampleKafkaTemplate");
			assertThat(context).hasBean("otherKafkaTemplate");
			assertThat(context).getBean("sampleKafkaTemplate").isInstanceOf(KafkaTemplate.class);
		});
	}

	/**
	 * 注册器只把序列化器的类型写进生产者配置,实例留给 Kafka 客户端创建:Serializer 实现 Closeable, 框架自己 new
	 * 出来再交出去,既会被判定为"未关闭的资源",也让多个生产者共享同一个实例。
	 */
	@Test
	void registeredTemplateConfiguresJsonValueSerializer() {
		this.contextRunner.withUserConfiguration(SampleConfiguration.class).run((context) -> {
			KafkaTemplate<?, ?> template = context.getBean("sampleKafkaTemplate", KafkaTemplate.class);

			assertThat(template.getProducerFactory().getConfigurationProperties())
				.containsEntry(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class)
				.containsEntry(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
		});
	}

	@Test
	void registersNewTopicPerEntityClassWithDefaults() {
		this.contextRunner.withUserConfiguration(SampleConfiguration.class).run((context) -> {
			assertThat(context).hasBean("sampleNewTopic");
			assertThat(context).hasBean("otherNewTopic");

			NewTopic sample = context.getBean("sampleNewTopic", NewTopic.class);
			assertThat(sample.name()).isEqualTo("sample");
			assertThat(sample.numPartitions()).isEqualTo(1);
			assertThat(sample.replicationFactor()).isEqualTo((short) 1);
		});
	}

	/**
	 * {@code butterfly.kafka.topic.*} 的三项配置都要能覆盖默认值,且按实体类分别生效。
	 */
	@Test
	void topicConfigurationOverridesNamePartitionsAndReplicasPerEntity() {
		this.contextRunner.withUserConfiguration(SampleConfiguration.class)
			.withPropertyValues("butterfly.kafka.topic.partitions=3", "butterfly.kafka.topic.replicas=2",
					"butterfly.kafka.topic.entities.sample.name=butterfly-sample",
					"butterfly.kafka.topic.entities.sample.replicas=4")
			.run((context) -> {
				NewTopic sample = context.getBean("sampleNewTopic", NewTopic.class);
				assertThat(sample.name()).isEqualTo("butterfly-sample");
				assertThat(sample.numPartitions()).isEqualTo(3);
				assertThat(sample.replicationFactor()).isEqualTo((short) 4);

				NewTopic other = context.getBean("otherNewTopic", NewTopic.class);
				assertThat(other.name()).isEqualTo("other");
				assertThat(other.numPartitions()).isEqualTo(3);
				assertThat(other.replicationFactor()).isEqualTo((short) 2);
			});
	}

	@Test
	void topicEntityKeyIsCaseInsensitive() {
		this.contextRunner.withUserConfiguration(SampleConfiguration.class)
			.withPropertyValues("butterfly.kafka.topic.entities.Sample.name=butterfly-sample")
			.run((context) -> assertThat(context.getBean("sampleNewTopic", NewTopic.class).name())
				.isEqualTo("butterfly-sample"));
	}

	@Test
	void noEntityClassesRegistersNothing() {
		this.contextRunner.withUserConfiguration(EmptyConfiguration.class)
			.run((context) -> assertThat(context).doesNotHaveBean("sampleKafkaTemplate")
				.doesNotHaveBean("sampleNewTopic")
				.doesNotHaveBean("sampleRetryTopicConfiguration")
				.doesNotHaveBean("sampleRetryTopics"));
	}

	/**
	 * 每个实体类都要默认拿到重试配置与重试主题,消费端因此无需自己标注 {@code @RetryableTopic}.
	 */
	@Test
	void registersRetryConfigurationAndRetryTopicsPerEntity() {
		this.contextRunner.withUserConfiguration(SampleConfiguration.class).run((context) -> {
			assertThat(context).hasBean("sampleRetryTopicConfiguration");
			assertThat(context).hasBean("otherRetryTopicConfiguration");
			assertThat(context.getBean("sampleRetryTopicConfiguration")).isInstanceOf(RetryTopicConfiguration.class);

			assertThat(context).hasBean("sampleRetryTopics");
			assertThat(context.getBean("sampleRetryTopics")).isInstanceOf(KafkaAdmin.NewTopics.class);
		});
	}

	/**
	 * 默认退避为 1s 起、倍率 2.0、上限 30s,{@code attempts=3} 时应当得到 retry-1000 与 retry-2000 两个重试主题
	 * 外加一个死信主题,且主题名严格按 {@code <主主题>-<后缀>} 拼接.
	 */
	@Test
	void retryTopicsFollowDefaultBackOffAndSuffixing() {
		this.contextRunner.withUserConfiguration(SampleConfiguration.class).run((context) -> {
			List<String> names = retryTopicNames(context, "sampleRetryTopics");

			assertThat(names).containsExactly("sample-retry-1000", "sample-retry-2000", "sample-dlt");
		});
	}

	/**
	 * {@code attempts}、起始间隔与倍率都必须来自配置,而不是写死的默认值.
	 */
	@Test
	void retryTopicsFollowConfiguredAttemptsAndBackOff() {
		this.contextRunner.withUserConfiguration(SampleConfiguration.class)
			.withPropertyValues("butterfly.kafka.topic.retry.attempts=4",
					"butterfly.kafka.topic.retry.back-off.delay=500ms",
					"butterfly.kafka.topic.retry.back-off.multiplier=3.0")
			.run((context) -> assertThat(retryTopicNames(context, "sampleRetryTopics"))
				.containsExactly("sample-retry-500", "sample-retry-1500", "sample-retry-4500", "sample-dlt"));
	}

	/**
	 * 命名策略可切换:按序号命名时重试主题为 {@code -retry-0/1/...},且自定义后缀要生效.
	 */
	@Test
	void retryTopicNamingStrategyAndSuffixAreConfigurable() {
		this.contextRunner.withUserConfiguration(SampleConfiguration.class)
			.withPropertyValues("butterfly.kafka.topic.retry.attempts=3",
					"butterfly.kafka.topic.retry.topic-suffixing-strategy=suffix_with_index_value",
					"butterfly.kafka.topic.retry.retry-topic-suffix=-again",
					"butterfly.kafka.topic.retry.dlt-topic-suffix=-dead")
			.run((context) -> assertThat(retryTopicNames(context, "sampleRetryTopics"))
				.containsExactly("sample-again-0", "sample-again-1", "sample-dead"));
	}

	/**
	 * 实体类级配置要覆盖全局配置,未覆盖的项仍回落全局.
	 * <p>
	 * {@code attempts=2} 只产生一次重试,Spring Kafka 对"仅一个重试主题"的情况不再拼接延迟值,主题名退化为
	 * {@code <主主题>-retry},这里顺带把这个行为固定下来.
	 */
	@Test
	void entityRetryOverridesGlobalRetry() {
		this.contextRunner.withUserConfiguration(SampleConfiguration.class)
			.withPropertyValues("butterfly.kafka.topic.retry.attempts=3",
					"butterfly.kafka.topic.retry.back-off.delay=1s",
					"butterfly.kafka.topic.entities.other.retry.attempts=2",
					"butterfly.kafka.topic.entities.other.retry.back-off.delay=250ms")
			.run((context) -> {
				assertThat(retryTopicNames(context, "sampleRetryTopics")).containsExactly("sample-retry-1000",
						"sample-retry-2000", "sample-dlt");
				assertThat(retryTopicNames(context, "otherRetryTopics")).containsExactly("other-retry", "other-dlt");
			});
	}

	/**
	 * 死信处理器按 {@code beanName#methodName} 解析;格式非法时要尽早报错而不是静默失效.
	 */
	@Test
	void invalidDltHandlerFailsFast() {
		this.contextRunner.withUserConfiguration(SampleConfiguration.class)
			.withPropertyValues("butterfly.kafka.topic.retry.dlt-handler=no-separator")
			.run((context) -> assertThatThrownBy(() -> context.getBean("sampleRetryTopicConfiguration"))
				.hasRootCauseInstanceOf(IllegalStateException.class)
				.hasStackTraceContaining("dlt-handler"));
	}

	private List<String> retryTopicNames(ApplicationContext context, String beanName) {
		KafkaAdmin.NewTopics newTopics = context.getBean(beanName, KafkaAdmin.NewTopics.class);
		Collection<NewTopic> topics = ReflectionTestUtils.invokeMethod(newTopics, "getNewTopics");
		return topics.stream().map(NewTopic::name).toList();
	}

	@Configuration(proxyBeanMethods = false)
	@EnableKafkaTemplates({ Sample.class, Other.class })
	static class SampleConfiguration {

	}

	@Configuration(proxyBeanMethods = false)
	@EnableKafkaTemplates
	static class EmptyConfiguration {

	}

	@Configuration(proxyBeanMethods = false)
	static class ProducerFactoryConfiguration {

		@Bean
		ProducerFactory<Object, Object> producerFactory() {
			return new DefaultKafkaProducerFactory<>(Map.of());
		}

	}

	static class Sample {

	}

	static class Other {

	}

}
