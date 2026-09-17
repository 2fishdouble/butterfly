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

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.kafka.autoconfigure.DefaultKafkaConsumerFactoryCustomizer;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.kafka.config.ContainerCustomizer;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 消费端默认值自动配置:使用方无需再写 {@code spring.kafka.consumer.*} 与 {@code spring.kafka.listener.*}.
 * <p>
 * 只在对应配置缺失时补齐,使用方显式配置过的项一律不覆盖:
 * <ul>
 * <li>value 反序列化器默认 {@link JacksonJsonDeserializer}(信任所有包),key 保持 Boot 默认的
 * {@link StringDeserializer};</li>
 * <li>{@code group.id} 默认取 {@code spring.application.name},取不到时用
 * {@value #FALLBACK_GROUP_ID};</li>
 * <li>{@code auto.offset.reset} 默认 {@code earliest};</li>
 * <li>监听容器默认 {@link ContainerProperties.AckMode#MANUAL},即由监听方法自行调用
 * {@code Acknowledgment#acknowledge()};要恢复自动提交就配置
 * {@code spring.kafka.listener.ack-mode}。</li>
 * </ul>
 * <p>
 * 全部通过 Spring Boot 自身的扩展点({@link DefaultKafkaConsumerFactoryCustomizer} 与
 * {@link ContainerCustomizer})注入,不替换 Boot 注册的任何 Bean。使用方自定义
 * {@code DefaultKafkaConsumerFactoryCustomizer} 会与本配置叠加;而 {@link ContainerCustomizer} 在
 * Boot 里是 单例扩展点,因此使用方一旦自定义,本配置对应 Bean 自动退让,不会造成重复 Bean 冲突。
 */
@AutoConfiguration(after = KafkaAutoConfiguration.class)
@ConditionalOnClass(KafkaTemplate.class)
public class ButterflyKafkaAutoConfiguration {

	/**
	 * 取不到 {@code spring.application.name} 时使用的兜底消费组名.
	 */
	private static final String FALLBACK_GROUP_ID = "butterfly";

	private static final String APPLICATION_NAME_PROPERTY = "spring.application.name";

	private static final String VALUE_DESERIALIZER_PROPERTY = "spring.kafka.consumer.value-deserializer";

	/**
	 * 补齐消费端默认值:消费组名、起始位移与 value 反序列化器.
	 * @param properties 容器中的 Kafka 配置,用于判断哪些项是使用方显式配置的
	 * @param environment 用于解析默认消费组名
	 * @return 消费工厂定制器
	 */
	@Bean
	DefaultKafkaConsumerFactoryCustomizer butterflyKafkaConsumerFactoryCustomizer(KafkaProperties properties,
			Environment environment) {
		return (factory) -> applyConsumerDefaults(factory, properties, environment);
	}

	/**
	 * 把监听容器默认设为 {@link ContainerProperties.AckMode#MANUAL}.
	 * <p>
	 * 使用方配置了 {@code spring.kafka.listener.ack-mode} 时不覆盖;自定义 {@link ContainerCustomizer}
	 * Bean 时本 Bean 退让。
	 * @param properties 容器中的 Kafka 配置
	 * @return 容器定制器
	 */
	@Bean
	@ConditionalOnMissingBean(ContainerCustomizer.class)
	ContainerCustomizer<Object, Object, ConcurrentMessageListenerContainer<Object, Object>> butterflyManualAckContainerCustomizer(
			KafkaProperties properties) {
		return (container) -> {
			if (properties.getListener().getAckMode() == null) {
				container.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
			}
		};
	}

	private static <K, V> void applyConsumerDefaults(DefaultKafkaConsumerFactory<K, V> factory,
			KafkaProperties properties, Environment environment) {
		KafkaProperties.Consumer consumer = properties.getConsumer();

		Map<String, Object> defaults = new LinkedHashMap<>();
		if (consumer.getGroupId() == null) {
			defaults.put(ConsumerConfig.GROUP_ID_CONFIG,
					environment.getProperty(APPLICATION_NAME_PROPERTY, FALLBACK_GROUP_ID));
		}
		if (consumer.getAutoOffsetReset() == null) {
			defaults.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		}
		if (!defaults.isEmpty()) {
			factory.updateConfigs(defaults);
		}

		// Boot 的 Consumer 构造器把 value 也初始化成 StringDeserializer,因此无法用"当前值是否为默认值"来判断
		// 使用方有没有配过;直接看属性本身是否出现过,没出现过才装 JSON 默认值
		if (!environment.containsProperty(VALUE_DESERIALIZER_PROPERTY)) {
			factory.setValueDeserializer(jsonDeserializer());
		}
	}

	private static <V> JacksonJsonDeserializer<V> jsonDeserializer() {
		return new JacksonJsonDeserializer<V>().trustedPackages("*");
	}

}
