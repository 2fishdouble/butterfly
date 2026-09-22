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

package io.github.butterfly.rocketmq.autoconfigure;

import lombok.Data;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * RocketMQ 主题与死信配置,前缀 {@code butterfly.rocketmq}.
 * <p>
 * 供 {@link EnableRocketMqTemplates} 声明式建立主题时使用:注册器会为每个实体类解析出一份 {@link TopicDefinition},再由
 * {@link RocketMqTopicInitializer} 按需把主主题创建到 Broker 上。
 * <ul>
 * <li>主主题名默认取实体类简单名首字母小写,例如 {@code Computer} → {@code computer};</li>
 * <li>消费组名默认取同名的全局前缀 + 实体类简单名首字母小写;</li>
 * <li>死信主题名默认是 {@code %DLQ%<消费组>},重试主题名默认是 {@code %RETRY%<消费组>};这两者是 RocketMQ
 * 的<b>系统主题</b>,由 Broker 自行创建,本模块只推导名称、不尝试创建;</li>
 * <li>队列(分区)数默认 {@value #DEFAULT_QUEUE_COUNT},消费标签默认 {@code *}(不过滤);</li>
 * <li>是否在启动时创建主主题默认 {@code true}。</li>
 * </ul>
 * <p>
 * 本类的可空性只出现在字段、方法参数与方法返回值上:覆盖项字段可空表示"未配置",而取值辅助方法一律把可空值收在 参数里,方法体内不再出现可空的局部变量。
 */
@Data
@ConfigurationProperties(prefix = "butterfly.rocketmq")
public class RocketMqProperties {

	/**
	 * 死信主题的固定前缀;RocketMQ 以 {@code %DLQ%<消费组>} 命名死信主题,该主题由 Broker 自动创建.
	 */
	private static final String DEAD_LETTER_TOPIC_PREFIX = "%DLQ%";

	/**
	 * 重试主题的固定前缀;RocketMQ 以 {@code %RETRY%<消费组>} 命名重试主题,该主题同样由 Broker 自动创建.
	 */
	private static final String RETRY_TOPIC_PREFIX = "%RETRY%";

	/**
	 * 主题队列(分区)数的默认值.
	 */
	private static final int DEFAULT_QUEUE_COUNT = 4;

	/**
	 * 消费标签的默认值,{@code *} 表示不过滤.
	 */
	private static final String DEFAULT_TAG = "*";

	/**
	 * 消费组名称的全局前缀,默认空串,可被 {@link Entity} 中的同名配置覆盖.
	 */
	private String consumerGroupPrefix = "";

	/**
	 * 创建主主题时的队列(分区)数,可被 {@link Entity} 中的同名配置覆盖.
	 */
	private int queueCount = DEFAULT_QUEUE_COUNT;

	/**
	 * 是否在启动时创建主主题,可被 {@link Entity} 中的同名配置覆盖.
	 */
	private boolean createTopics = true;

	/**
	 * 消费标签的全局默认值,可被 {@link Entity} 中的同名配置覆盖.
	 */
	private String tag = DEFAULT_TAG;

	/**
	 * 按实体类覆盖主题定义,key 为实体类简单名(忽略大小写),例如 {@code computer}.
	 */
	private Map<String, Entity> entities = new LinkedHashMap<>();

	/**
	 * 解析某个实体类最终生效的主题定义,取值优先级为:实体类覆盖 &gt; 全局默认 &gt; 内置默认值.
	 * @param entityClass {@link EnableRocketMqTemplates} 中声明的实体类
	 * @return 解析后的主题名、消费组名、死信与重试主题名、标签、队列数与建主题开关
	 * @throws IllegalStateException 主题名或消费组名为空,或队列数不为正数时抛出
	 */
	public TopicDefinition resolve(Class<?> entityClass) {
		return buildTopicDefinition(findEntity(entityClass), entityClass);
	}

	private TopicDefinition buildTopicDefinition(@Nullable Entity entity, Class<?> entityClass) {
		String baseName = uncapitalize(entityClass.getSimpleName());

		String topic = coalesce(valueOf(entity, Entity::getTopic), baseName);
		if (!StringUtils.hasText(topic)) {
			throw new IllegalStateException(
					"'butterfly.rocketmq.entities.*.topic' must not be empty for " + entityClass.getName());
		}

		String consumerGroup = coalesce(valueOf(entity, Entity::getConsumerGroup), this.consumerGroupPrefix + baseName);
		if (!StringUtils.hasText(consumerGroup)) {
			throw new IllegalStateException(
					"'butterfly.rocketmq.entities.*.consumer-group' must not be empty for " + entityClass.getName());
		}

		// 死信与重试主题是 RocketMQ 的系统主题:Broker 会在消费组首次订阅时自行创建,这里只推导名称
		String deadLetterTopic = coalesce(valueOf(entity, Entity::getDeadLetterTopic),
				DEAD_LETTER_TOPIC_PREFIX + consumerGroup);
		String retryTopic = coalesce(valueOf(entity, Entity::getRetryTopic), RETRY_TOPIC_PREFIX + consumerGroup);

		String resolvedTag = coalesce(valueOf(entity, Entity::getTag), this.tag);

		int resolvedQueueCount = coalesce(valueOf(entity, Entity::getQueueCount), this.queueCount);
		if (resolvedQueueCount < 1) {
			throw new IllegalStateException("'butterfly.rocketmq.queue-count' must be positive but was "
					+ resolvedQueueCount + " for " + entityClass.getName());
		}

		boolean resolvedCreateTopic = coalesce(valueOf(entity, Entity::getCreateTopic), this.createTopics);

		return new TopicDefinition(topic, consumerGroup, deadLetterTopic, retryTopic, resolvedTag, resolvedQueueCount,
				resolvedCreateTopic);
	}

	/**
	 * 从实体类覆盖项里读一个字段.
	 * @param <T> 字段类型
	 * @param entity 实体类覆盖项,可为空
	 * @param getter 字段读取器
	 * @return 字段值,实体类覆盖项为空时为 {@code null}
	 */
	private static <T> @Nullable T valueOf(@Nullable Entity entity, Function<Entity, @Nullable T> getter) {
		return (entity != null) ? getter.apply(entity) : null;
	}

	/**
	 * 取第一个非空值,为空时取兜底值.
	 * @param <T> 取值类型
	 * @param value 取值,可为空
	 * @param fallback 兜底值,必定非空
	 * @return 非空取值
	 */
	private static <T> T coalesce(@Nullable T value, T fallback) {
		return (value != null) ? value : fallback;
	}

	private @Nullable Entity findEntity(Class<?> entityClass) {
		return this.entities.entrySet()
			.stream()
			.filter((entry) -> entry.getKey().equalsIgnoreCase(entityClass.getSimpleName()))
			.map(Map.Entry::getValue)
			.findFirst()
			.orElse(null);
	}

	private String uncapitalize(String str) {
		if (str.isEmpty()) {
			return str;
		}
		return Character.toLowerCase(str.charAt(0)) + str.substring(1);
	}

	/**
	 * 单个实体类的覆盖配置,各字段均可选,为空时回落到全局默认值.
	 */
	@Data
	public static class Entity {

		/**
		 * 主主题名;为空时取实体类简单名首字母小写.
		 */
		private @Nullable String topic;

		/**
		 * 消费组名;为空时取 {@code butterfly.rocketmq.consumer-group-prefix} + 实体类简单名首字母小写.
		 */
		private @Nullable String consumerGroup;

		/**
		 * 死信主题名;为空时取 {@code %DLQ%<消费组>}.
		 */
		private @Nullable String deadLetterTopic;

		/**
		 * 重试主题名;为空时取 {@code %RETRY%<消费组>}.
		 */
		private @Nullable String retryTopic;

		/**
		 * 消费标签;为空时取 {@link RocketMqProperties#tag}.
		 */
		private @Nullable String tag;

		/**
		 * 主主题的队列(分区)数;为空时取 {@link RocketMqProperties#queueCount}.
		 */
		private @Nullable Integer queueCount;

		/**
		 * 是否创建该实体类的主主题;为空时取 {@link RocketMqProperties#createTopics}.
		 */
		private @Nullable Boolean createTopic;

	}

	/**
	 * 解析后的主题定义:所有字段都必定有值,未配置项一律落到内置默认值.
	 *
	 * @param topic 主主题名
	 * @param consumerGroup 消费组名
	 * @param deadLetterTopic 死信主题名({@code %DLQ%<消费组>},由 Broker 创建)
	 * @param retryTopic 重试主题名({@code %RETRY%<消费组>},由 Broker 创建)
	 * @param tag 消费标签,{@code *} 表示不过滤
	 * @param queueCount 主主题的队列(分区)数
	 * @param createTopic 是否在启动时创建主主题
	 */
	public record TopicDefinition(String topic, String consumerGroup, String deadLetterTopic, String retryTopic,
			String tag, int queueCount, boolean createTopic) {

		/**
		 * 发送目的地,格式为 {@code topicName:tags};未配置标签或标签为 {@code *} 时只返回主题名.
		 * @return 可直接交给 {@code RocketMQClientTemplate} 的目的地
		 */
		public String destination() {
			return (StringUtils.hasText(this.tag) && !DEFAULT_TAG.equals(this.tag)) ? this.topic + ":" + this.tag
					: this.topic;
		}

	}

}
