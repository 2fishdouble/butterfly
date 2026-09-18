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

import lombok.Data;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.kafka.retrytopic.SameIntervalTopicReuseStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Kafka 主题与重试配置,前缀 {@code butterfly.kafka.topic}.
 * <p>
 * 供 {@link EnableKafkaTemplates} 自动创建主题时使用:注册器会为每个实体类注册一个 {@code NewTopic} Bean,
 * 主题名、分区数与副本数三项均可选,未配置时按下面的规则取默认值。
 * <ul>
 * <li>主题名默认取实体类简单名首字母小写,例如 {@code Computer} → {@code computer};也可在 {@link #entities}
 * 中显式指定完整主题名;</li>
 * <li>分区数与副本数默认取 {@link #partitions} / {@link #replicas},可在 {@link #entities}
 * 中按实体类覆盖。</li>
 * </ul>
 * <p>
 * 自动创建由 {@code KafkaAdmin} 执行,因此仍受 Spring Boot 的 {@code spring.kafka.admin.auto-create}
 * (默认 {@code true})统管:把它设为 {@code false} 即可在保留 {@code NewTopic} Bean 定义的同时关闭建主题动作。
 * <p>
 * 除主主题外,注册器还会为每个实体类默认建立一整套非阻塞重试主题({@code <主题>-retry-<延迟>})与死信主题 ({@code <主题>-dlt}),重试参数由
 * {@link #retry} 与 {@link Entity} 中的同名覆盖项控制,详见 {@link #resolveRetry(Class)}。
 * <p>
 * 本类的可空性只出现在字段、方法参数与方法返回值上:覆盖项字段可空表示"未配置",而取值辅助方法一律把可空值收在 参数里,方法体内不再出现可空的局部变量。
 */
@Data
@ConfigurationProperties(prefix = "butterfly.kafka.topic")
public class KafkaTopicProperties {

	/**
	 * 重试总次数(含首次投递)的默认值.
	 */
	private static final int DEFAULT_RETRY_ATTEMPTS = 3;

	/**
	 * 重试起始间隔的默认值.
	 */
	private static final Duration DEFAULT_RETRY_DELAY = Duration.ofSeconds(1);

	/**
	 * 重试间隔倍率的默认值,大于 {@code 1} 时表示指数退避.
	 */
	private static final double DEFAULT_RETRY_MULTIPLIER = 2.0;

	/**
	 * 重试间隔上限的默认值,避免次数多时间隔无限增长.
	 */
	private static final Duration DEFAULT_RETRY_MAX_DELAY = Duration.ofSeconds(30);

	/**
	 * 重试主题命名策略的默认值.
	 */
	private static final TopicSuffixingStrategy DEFAULT_TOPIC_SUFFIXING_STRATEGY = TopicSuffixingStrategy.SUFFIX_WITH_DELAY_VALUE;

	/**
	 * 相同间隔复用策略的默认值.
	 */
	private static final SameIntervalTopicReuseStrategy DEFAULT_SAME_INTERVAL_TOPIC_REUSE_STRATEGY = SameIntervalTopicReuseStrategy.SINGLE_TOPIC;

	/**
	 * 重试主题后缀的默认值.
	 */
	private static final String DEFAULT_RETRY_TOPIC_SUFFIX = "-retry";

	/**
	 * 死信主题后缀的默认值.
	 */
	private static final String DEFAULT_DLT_TOPIC_SUFFIX = "-dlt";

	/**
	 * 死信容器是否随应用启动的默认值,与 Spring Kafka 未显式指定时的实际行为一致.
	 */
	private static final boolean DEFAULT_AUTO_START_DLT_HANDLER = true;

	/**
	 * 自动创建主题时的默认分区数,可被 {@link #entities} 中的同名配置覆盖.
	 */
	private int partitions = 1;

	/**
	 * 自动创建主题时的默认副本数,可被 {@link #entities} 中的同名配置覆盖.
	 */
	private int replicas = 1;

	/**
	 * 重试主题的全局默认参数,可被 {@link Entity} 中同名配置按实体类覆盖.
	 */
	private Retry retry = new Retry();

	/**
	 * 按实体类覆盖主题定义,key 为实体类简单名(忽略大小写),例如 {@code computer}.
	 */
	private Map<String, Entity> entities = new LinkedHashMap<>();

	/**
	 * 解析某个实体类最终生效的主题定义.
	 * @param entityClass {@link EnableKafkaTemplates} 中声明的实体类
	 * @return 解析后的主题名、分区数与副本数
	 */
	public TopicDefinition resolve(Class<?> entityClass) {
		return buildTopicDefinition(findEntity(entityClass), entityClass);
	}

	/**
	 * 解析某个实体类最终生效的重试参数,取值优先级为:实体类覆盖 &gt; 全局 {@link #retry} &gt; 内置默认值.
	 * <p>
	 * 除 {@link RetryDefinition#dltHandler()} 外,返回的每个字段都必定有值:未配置的项一律落到内置默认值,因此
	 * 使用方什么都不配也能拿到可用的重试与死信策略。重试主题的分区数与副本数在未单独配置时跟随该实体类的主主题。
	 * @param entityClass {@link EnableKafkaTemplates} 中声明的实体类
	 * @return 解析后的重试参数
	 * @throws IllegalStateException 重试次数不为正数时抛出
	 */
	public RetryDefinition resolveRetry(Class<?> entityClass) {
		return buildRetryDefinition(findEntity(entityClass), entityClass);
	}

	private TopicDefinition buildTopicDefinition(@Nullable Entity entity, Class<?> entityClass) {
		String name = (entity != null && StringUtils.hasText(entity.getName())) ? entity.getName()
				: uncapitalize(entityClass.getSimpleName());
		int resolvedPartitions = (entity != null && entity.getPartitions() != null) ? entity.getPartitions()
				: this.partitions;
		int resolvedReplicas = (entity != null && entity.getReplicas() != null) ? entity.getReplicas() : this.replicas;

		return new TopicDefinition(name, resolvedPartitions, resolvedReplicas);
	}

	private RetryDefinition buildRetryDefinition(@Nullable Entity entity, Class<?> entityClass) {
		TopicDefinition topic = buildTopicDefinition(entity, entityClass);
		Retry globalRetry = this.retry;

		int attempts = coalesce(valueOf(retryOf(entity), Retry::getAttempts), valueOf(globalRetry, Retry::getAttempts),
				DEFAULT_RETRY_ATTEMPTS);
		if (attempts < 1) {
			throw new IllegalStateException("'butterfly.kafka.topic.retry.attempts' must be positive but was "
					+ attempts + " for " + entityClass.getName());
		}
		Duration delay = coalesce(backOffValueOf(retryOf(entity), BackOff::getDelay),
				backOffValueOf(globalRetry, BackOff::getDelay), DEFAULT_RETRY_DELAY);
		Double multiplier = coalesce(backOffValueOf(retryOf(entity), BackOff::getMultiplier),
				backOffValueOf(globalRetry, BackOff::getMultiplier), DEFAULT_RETRY_MULTIPLIER);
		Duration maxDelay = coalesce(backOffValueOf(retryOf(entity), BackOff::getMaxDelay),
				backOffValueOf(globalRetry, BackOff::getMaxDelay), DEFAULT_RETRY_MAX_DELAY);
		TopicSuffixingStrategy suffixingStrategy = coalesce(valueOf(retryOf(entity), Retry::getTopicSuffixingStrategy),
				valueOf(globalRetry, Retry::getTopicSuffixingStrategy), DEFAULT_TOPIC_SUFFIXING_STRATEGY);
		SameIntervalTopicReuseStrategy reuseStrategy = coalesce(
				valueOf(retryOf(entity), Retry::getSameIntervalTopicReuseStrategy),
				valueOf(globalRetry, Retry::getSameIntervalTopicReuseStrategy),
				DEFAULT_SAME_INTERVAL_TOPIC_REUSE_STRATEGY);
		String retryTopicSuffix = coalesce(valueOf(retryOf(entity), Retry::getRetryTopicSuffix),
				valueOf(globalRetry, Retry::getRetryTopicSuffix), DEFAULT_RETRY_TOPIC_SUFFIX);
		String dltTopicSuffix = coalesce(valueOf(retryOf(entity), Retry::getDltTopicSuffix),
				valueOf(globalRetry, Retry::getDltTopicSuffix), DEFAULT_DLT_TOPIC_SUFFIX);
		boolean autoStartDltHandler = coalesce(valueOf(retryOf(entity), Retry::getAutoStartDltHandler),
				valueOf(globalRetry, Retry::getAutoStartDltHandler), DEFAULT_AUTO_START_DLT_HANDLER);
		int retryPartitions = coalesce(valueOf(retryOf(entity), Retry::getPartitions),
				valueOf(globalRetry, Retry::getPartitions), topic.partitions());
		int retryReplicas = coalesce(valueOf(retryOf(entity), Retry::getReplicas),
				valueOf(globalRetry, Retry::getReplicas), topic.replicas());

		return new RetryDefinition(attempts, delay, multiplier, maxDelay, suffixingStrategy, reuseStrategy,
				retryTopicSuffix, dltTopicSuffix,
				// 死信处理器没有"内置默认值"可言:留空即表示沿用框架自带的日志处理器
				coalesceNullable(valueOf(retryOf(entity), Retry::getDltHandler),
						valueOf(globalRetry, Retry::getDltHandler)),
				autoStartDltHandler, retryPartitions, retryReplicas);
	}

	/**
	 * 取实体类上的重试覆盖配置.
	 * @param entity 实体类覆盖项,可为空
	 * @return 该实体类的重试覆盖配置,实体类无配置时为 {@code null}
	 */
	private static @Nullable Retry retryOf(@Nullable Entity entity) {
		return (entity != null) ? entity.getRetry() : null;
	}

	/**
	 * 从重试配置里读一个字段.
	 * @param <T> 字段类型
	 * @param retry 重试配置,可为空
	 * @param getter 字段读取器
	 * @return 字段值,重试配置为空时为 {@code null}
	 */
	private static <T> @Nullable T valueOf(@Nullable Retry retry, Function<Retry, @Nullable T> getter) {
		return (retry != null) ? getter.apply(retry) : null;
	}

	/**
	 * 从退避配置里读一个字段.
	 * @param <T> 字段类型
	 * @param backOff 退避配置,可为空
	 * @param getter 字段读取器
	 * @return 字段值,退避配置为空时为 {@code null}
	 */
	private static <T> @Nullable T valueOf(@Nullable BackOff backOff, Function<BackOff, @Nullable T> getter) {
		return (backOff != null) ? getter.apply(backOff) : null;
	}

	/**
	 * 从重试配置的退避配置里读一个字段.
	 * @param <T> 字段类型
	 * @param retry 重试配置,可为空
	 * @param getter 退避字段读取器
	 * @return 字段值,重试配置或退避配置为空时为 {@code null}
	 */
	private static <T> @Nullable T backOffValueOf(@Nullable Retry retry, Function<BackOff, @Nullable T> getter) {
		return valueOf(valueOf(retry, Retry::getBackOff), getter);
	}

	/**
	 * 取第一个非空值,都为空时取兜底值.
	 * @param <T> 取值类型
	 * @param first 优先取值,可为空
	 * @param second 次选取值,可为空
	 * @param fallback 兜底值,必定非空,因此返回值必定非空
	 * @return 首个非空值或兜底值,必定非空
	 */
	private static <T> T coalesce(@Nullable T first, @Nullable T second, T fallback) {
		if (first != null) {
			return first;
		}
		return (second != null) ? second : fallback;
	}

	/**
	 * 取第一个非空值,都为空时返回 {@code null};用于本身就没有默认值可言的项.
	 * @param <T> 取值类型
	 * @param first 优先取值,可为空
	 * @param second 次选取值,可为空
	 * @return 首个非空值,都为空时为 {@code null}
	 */
	private static <T> @Nullable T coalesceNullable(@Nullable T first, @Nullable T second) {
		return (first != null) ? first : second;
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
		 * 主题名;为空时取实体类简单名首字母小写.
		 */
		private @Nullable String name;

		/**
		 * 分区数;为空时取 {@link KafkaTopicProperties#partitions}.
		 */
		private @Nullable Integer partitions;

		/**
		 * 副本数;为空时取 {@link KafkaTopicProperties#replicas}.
		 */
		private @Nullable Integer replicas;

		/**
		 * 该实体类的重试覆盖配置;为空时全部取 {@link KafkaTopicProperties#retry}.
		 */
		private @Nullable Retry retry;

	}

	/**
	 * 重试主题配置,所有字段均可选:实体类级未配置的项回落到全局级,全局级未配置的项回落到内置默认值.
	 */
	@Data
	public static class Retry {

		/**
		 * 重试总次数(含首次投递);须为正数,{@code 1} 表示不重试,默认 3.
		 */
		private @Nullable Integer attempts;

		/**
		 * 退避参数.
		 */
		private @Nullable BackOff backOff;

		/**
		 * 重试主题命名策略,默认 {@link TopicSuffixingStrategy#SUFFIX_WITH_DELAY_VALUE}.
		 */
		private @Nullable TopicSuffixingStrategy topicSuffixingStrategy;

		/**
		 * 相同重试间隔是否复用同一个主题,默认 {@link SameIntervalTopicReuseStrategy#SINGLE_TOPIC}.
		 */
		private @Nullable SameIntervalTopicReuseStrategy sameIntervalTopicReuseStrategy;

		/**
		 * 重试主题后缀,默认 {@code -retry}.
		 */
		private @Nullable String retryTopicSuffix;

		/**
		 * 死信主题后缀,默认 {@code -dlt}.
		 */
		private @Nullable String dltTopicSuffix;

		/**
		 * 死信处理器,格式为 {@code beanName#methodName};未配置时使用框架内置的日志处理器.
		 */
		private @Nullable String dltHandler;

		/**
		 * 死信监听容器是否随应用启动,默认 {@code true}.
		 */
		private @Nullable Boolean autoStartDltHandler;

		/**
		 * 重试与死信主题的分区数;为空时跟随该实体类的主主题.
		 */
		private @Nullable Integer partitions;

		/**
		 * 重试与死信主题的副本数;为空时跟随该实体类的主主题.
		 */
		private @Nullable Integer replicas;

	}

	/**
	 * 退避参数.
	 */
	@Data
	public static class BackOff {

		/**
		 * 首次重试的间隔,默认 {@code 1s}.
		 */
		private @Nullable Duration delay;

		/**
		 * 间隔增长倍率,大于 {@code 1} 时按指数退避,否则按固定间隔,默认 {@code 2.0}.
		 */
		private @Nullable Double multiplier;

		/**
		 * 间隔上限,默认 {@code 30s};配置值不大于起始间隔时按固定间隔处理.
		 */
		private @Nullable Duration maxDelay;

	}

	/**
	 * 解析后的主题定义.
	 *
	 * @param name 主题名
	 * @param partitions 分区数
	 * @param replicas 副本数
	 */
	public record TopicDefinition(String name, int partitions, int replicas) {
	}

	/**
	 * 解析后的重试参数:除 {@code dltHandler} 外所有字段都必定有值.
	 *
	 * @param attempts 重试总次数(含首次投递)
	 * @param delay 首次重试间隔
	 * @param multiplier 间隔增长倍率
	 * @param maxDelay 间隔上限
	 * @param topicSuffixingStrategy 重试主题命名策略
	 * @param sameIntervalTopicReuseStrategy 相同间隔是否复用同一主题
	 * @param retryTopicSuffix 重试主题后缀
	 * @param dltTopicSuffix 死信主题后缀
	 * @param dltHandler 死信处理器({@code beanName#methodName});为 {@code null} 表示未配置,沿用框架内置的
	 * 日志处理器
	 * @param autoStartDltHandler 死信容器是否随应用启动
	 * @param partitions 重试与死信主题的分区数
	 * @param replicas 重试与死信主题的副本数
	 */
	public record RetryDefinition(int attempts, Duration delay, double multiplier, Duration maxDelay,
			TopicSuffixingStrategy topicSuffixingStrategy,
			SameIntervalTopicReuseStrategy sameIntervalTopicReuseStrategy, String retryTopicSuffix,
			String dltTopicSuffix, @Nullable String dltHandler, boolean autoStartDltHandler, int partitions,
			int replicas) {
	}

}
