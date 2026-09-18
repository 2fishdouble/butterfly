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

package io.github.butterfly.rabbitmq.autoconfigure;

import lombok.Data;
import org.jspecify.annotations.Nullable;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * RabbitMQ 拓扑与重试配置,前缀 {@code butterfly.rabbitmq}.
 * <p>
 * 供 {@link EnableRabbitMqTemplates} 声明式建立拓扑时使用:注册器会为每个实体类声明主交换机/队列/绑定、死信
 * 交换机/队列/绑定、可选的延时交换机/队列/绑定,以及专属的监听容器工厂。
 * <p>
 * 所有配置项均可选,未配置时按下面的规则取默认值。
 * <ul>
 * <li>主拓扑名称默认取实体类简单名首字母小写,例如 {@code Computer} → {@code computer};</li>
 * <li>死信交换机/队列/路由键默认在主拓扑名称后分别追加 {@code .dlx} / {@code .dlq} / {@code .dead};</li>
 * <li>延时交换机/队列/路由键默认在主拓扑名称后分别追加 {@code .delay};</li>
 * <li>交换机类型默认 {@link ExchangeType#TOPIC},队列默认持久化且不自动删除;</li>
 * <li>重试默认总投递 3 次、起始间隔 {@code 1s}、倍率 {@code 2.0}、上限 {@code 30s};</li>
 * <li>延时队列默认关闭,开启后默认 TTL {@code 30s};</li>
 * <li>监听容器默认单并发、预取 1、手动确认、失败不重回队列。</li>
 * </ul>
 * <p>
 * 本类的可空性只出现在字段、方法参数与方法返回值上。
 * <p>
 * 覆盖项字段可空表示"未配置",而取值辅助方法一律把可空值收在参数里,方法体内不再出现可空的局部变量。
 */
@Data
@ConfigurationProperties(prefix = "butterfly.rabbitmq")
public class RabbitMqProperties {

	/**
	 * 重试总投递次数(含首次)的默认值.
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
	 * 延时队列消息存活时间的默认值.
	 */
	private static final Duration DEFAULT_DELAY_TTL = Duration.ofSeconds(30);

	/**
	 * 监听容器并发消费者数的默认值.
	 */
	private static final int DEFAULT_LISTENER_CONCURRENCY = 1;

	/**
	 * 监听容器预取数量的默认值.
	 */
	private static final int DEFAULT_LISTENER_PREFETCH = 1;

	/**
	 * 监听容器失败后是否重回队列的默认值.
	 */
	private static final boolean DEFAULT_LISTENER_REQUEUE_REJECTED = false;

	/**
	 * 监听容器确认模式的默认值.
	 */
	private static final AcknowledgeMode DEFAULT_LISTENER_ACKNOWLEDGE_MODE = AcknowledgeMode.MANUAL;

	/**
	 * 死信拓扑名称的默认后缀.
	 */
	private static final String DEAD_LETTER_EXCHANGE_SUFFIX = ".dlx";

	private static final String DEAD_LETTER_QUEUE_SUFFIX = ".dlq";

	private static final String DEAD_LETTER_ROUTING_KEY_SUFFIX = ".dead";

	/**
	 * 延时拓扑名称的默认后缀.
	 */
	private static final String DELAY_SUFFIX = ".delay";

	/**
	 * 主交换机类型的全局默认值,可被 {@link Entity} 中的同名配置覆盖.
	 */
	private ExchangeType exchangeType = ExchangeType.TOPIC;

	/**
	 * 主拓扑是否持久化的全局默认值,可被 {@link Entity} 中的同名配置覆盖.
	 */
	private boolean durable = true;

	/**
	 * 主拓扑是否自动删除的全局默认值,可被 {@link Entity} 中的同名配置覆盖.
	 */
	private boolean autoDelete = false;

	/**
	 * 重试参数的全局默认值,可被 {@link Entity} 中的同名配置覆盖.
	 */
	private Retry retry = new Retry();

	/**
	 * 死信拓扑的全局默认值,可被 {@link Entity} 中的同名配置覆盖.
	 */
	private DeadLetter deadLetter = new DeadLetter();

	/**
	 * 延时拓扑的全局默认值,可被 {@link Entity} 中的同名配置覆盖.
	 */
	private Delay delay = new Delay();

	/**
	 * 监听容器参数的全局默认值,可被 {@link Entity} 中的同名配置覆盖.
	 */
	private Listener listener = new Listener();

	/**
	 * 按实体类覆盖拓扑定义,key 为实体类简单名(忽略大小写),例如 {@code computer}.
	 */
	private Map<String, Entity> entities = new LinkedHashMap<>();

	/**
	 * 解析某个实体类最终生效的完整拓扑定义,取值优先级为:实体类覆盖 &gt; 全局默认 &gt; 内置默认值.
	 * @param entityClass {@link EnableRabbitMqTemplates} 中声明的实体类
	 * @return 解析后的拓扑名称、交换机类型、重试、死信、延时与监听参数
	 * @throws IllegalStateException 重试次数不为正数,或延时的 TTL 不为正数时抛出
	 */
	public EntityDefinition resolve(Class<?> entityClass) {
		return buildEntityDefinition(findEntity(entityClass), entityClass);
	}

	private EntityDefinition buildEntityDefinition(@Nullable Entity entity, Class<?> entityClass) {
		String baseName = uncapitalize(entityClass.getSimpleName());
		String exchange = coalesce(valueOf(entity, Entity::getExchange), baseName);
		String queue = coalesce(valueOf(entity, Entity::getQueue), baseName);
		String routingKey = coalesce(valueOf(entity, Entity::getRoutingKey), baseName);
		ExchangeType resolvedExchangeType = coalesce(valueOf(entity, Entity::getExchangeType), this.exchangeType);
		boolean resolvedDurable = coalesce(valueOf(entity, Entity::getDurable), this.durable);
		boolean resolvedAutoDelete = coalesce(valueOf(entity, Entity::getAutoDelete), this.autoDelete);

		Retry globalRetry = this.retry;
		int attempts = coalesce(valueOf(retryOf(entity), Retry::getAttempts), valueOf(globalRetry, Retry::getAttempts),
				DEFAULT_RETRY_ATTEMPTS);
		if (attempts < 1) {
			throw new IllegalStateException("'butterfly.rabbitmq.retry.attempts' must be positive but was " + attempts
					+ " for " + entityClass.getName());
		}
		Duration retryDelay = coalesce(valueOf(retryOf(entity), Retry::getDelay), valueOf(globalRetry, Retry::getDelay),
				DEFAULT_RETRY_DELAY);
		double retryMultiplier = coalesce(valueOf(retryOf(entity), Retry::getMultiplier),
				valueOf(globalRetry, Retry::getMultiplier), DEFAULT_RETRY_MULTIPLIER);
		Duration retryMaxDelay = coalesce(valueOf(retryOf(entity), Retry::getMaxDelay),
				valueOf(globalRetry, Retry::getMaxDelay), DEFAULT_RETRY_MAX_DELAY);

		DeadLetter globalDeadLetter = this.deadLetter;
		boolean deadLetterEnabled = coalesce(valueOf(deadLetterOf(entity), DeadLetter::getEnabled),
				valueOf(globalDeadLetter, DeadLetter::getEnabled), Boolean.TRUE);
		String deadLetterExchange = coalesce(valueOf(deadLetterOf(entity), DeadLetter::getExchange),
				valueOf(globalDeadLetter, DeadLetter::getExchange), exchange + DEAD_LETTER_EXCHANGE_SUFFIX);
		String deadLetterQueue = coalesce(valueOf(deadLetterOf(entity), DeadLetter::getQueue),
				valueOf(globalDeadLetter, DeadLetter::getQueue), queue + DEAD_LETTER_QUEUE_SUFFIX);
		String deadLetterRoutingKey = coalesce(valueOf(deadLetterOf(entity), DeadLetter::getRoutingKey),
				valueOf(globalDeadLetter, DeadLetter::getRoutingKey), routingKey + DEAD_LETTER_ROUTING_KEY_SUFFIX);

		Delay globalDelay = this.delay;
		boolean delayEnabled = coalesce(valueOf(delayOf(entity), Delay::getEnabled),
				valueOf(globalDelay, Delay::getEnabled), Boolean.FALSE);
		String delayExchange = coalesce(valueOf(delayOf(entity), Delay::getExchange),
				valueOf(globalDelay, Delay::getExchange), exchange + DELAY_SUFFIX);
		String delayQueue = coalesce(valueOf(delayOf(entity), Delay::getQueue), valueOf(globalDelay, Delay::getQueue),
				queue + DELAY_SUFFIX);
		String delayRoutingKey = coalesce(valueOf(delayOf(entity), Delay::getRoutingKey),
				valueOf(globalDelay, Delay::getRoutingKey), routingKey + DELAY_SUFFIX);
		Duration delayTtl = coalesce(valueOf(delayOf(entity), Delay::getTtl), valueOf(globalDelay, Delay::getTtl),
				DEFAULT_DELAY_TTL);
		if (delayEnabled && (delayTtl.isZero() || delayTtl.isNegative())) {
			throw new IllegalStateException("'butterfly.rabbitmq.delay.ttl' must be positive but was " + delayTtl
					+ " for " + entityClass.getName());
		}

		Listener globalListener = this.listener;
		int concurrency = coalesce(valueOf(listenerOf(entity), Listener::getConcurrency),
				valueOf(globalListener, Listener::getConcurrency), DEFAULT_LISTENER_CONCURRENCY);
		int prefetch = coalesce(valueOf(listenerOf(entity), Listener::getPrefetch),
				valueOf(globalListener, Listener::getPrefetch), DEFAULT_LISTENER_PREFETCH);
		boolean requeueRejected = coalesce(valueOf(listenerOf(entity), Listener::getRequeueRejected),
				valueOf(globalListener, Listener::getRequeueRejected), DEFAULT_LISTENER_REQUEUE_REJECTED);
		AcknowledgeMode acknowledgeMode = coalesce(valueOf(listenerOf(entity), Listener::getAcknowledgeMode),
				valueOf(globalListener, Listener::getAcknowledgeMode), DEFAULT_LISTENER_ACKNOWLEDGE_MODE);

		return new EntityDefinition(exchange, resolvedExchangeType, resolvedDurable, resolvedAutoDelete, queue,
				routingKey, deadLetterEnabled, deadLetterExchange, deadLetterQueue, deadLetterRoutingKey, delayEnabled,
				delayExchange, delayQueue, delayRoutingKey, delayTtl, attempts, retryDelay, retryMultiplier,
				retryMaxDelay, concurrency, prefetch, requeueRejected, acknowledgeMode);
	}

	private static @Nullable Retry retryOf(@Nullable Entity entity) {
		return (entity != null) ? entity.getRetry() : null;
	}

	private static @Nullable DeadLetter deadLetterOf(@Nullable Entity entity) {
		return (entity != null) ? entity.getDeadLetter() : null;
	}

	private static @Nullable Delay delayOf(@Nullable Entity entity) {
		return (entity != null) ? entity.getDelay() : null;
	}

	private static @Nullable Listener listenerOf(@Nullable Entity entity) {
		return (entity != null) ? entity.getListener() : null;
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
	 * 从死信配置里读一个字段.
	 * @param <T> 字段类型
	 * @param deadLetter 死信配置,可为空
	 * @param getter 字段读取器
	 * @return 字段值,死信配置为空时为 {@code null}
	 */
	private static <T> @Nullable T valueOf(@Nullable DeadLetter deadLetter, Function<DeadLetter, @Nullable T> getter) {
		return (deadLetter != null) ? getter.apply(deadLetter) : null;
	}

	/**
	 * 从延时配置里读一个字段.
	 * @param <T> 字段类型
	 * @param delay 延时配置,可为空
	 * @param getter 字段读取器
	 * @return 字段值,延时配置为空时为 {@code null}
	 */
	private static <T> @Nullable T valueOf(@Nullable Delay delay, Function<Delay, @Nullable T> getter) {
		return (delay != null) ? getter.apply(delay) : null;
	}

	/**
	 * 从监听配置里读一个字段.
	 * @param <T> 字段类型
	 * @param listener 监听配置,可为空
	 * @param getter 字段读取器
	 * @return 字段值,监听配置为空时为 {@code null}
	 */
	private static <T> @Nullable T valueOf(@Nullable Listener listener, Function<Listener, @Nullable T> getter) {
		return (listener != null) ? getter.apply(listener) : null;
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
	 * 取第一个非空值,都为空时取兜底值;只有一个覆盖来源时使用.
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
	 * 交换机类型.
	 */
	public enum ExchangeType {

		/**
		 * 直连交换机,按路由键精确匹配.
		 */
		DIRECT,

		/**
		 * 主题交换机,按路由键通配匹配.
		 */
		TOPIC,

		/**
		 * 扇形交换机,忽略路由键,广播到所有绑定队列.
		 */
		FANOUT

	}

	/**
	 * 单个实体类的覆盖配置,各字段均可选,为空时回落到全局默认值.
	 */
	@Data
	public static class Entity {

		/**
		 * 主交换机名称;为空时取实体类简单名首字母小写.
		 */
		private @Nullable String exchange;

		/**
		 * 主队列名称;为空时取实体类简单名首字母小写.
		 */
		private @Nullable String queue;

		/**
		 * 主绑定路由键;为空时取实体类简单名首字母小写.
		 */
		private @Nullable String routingKey;

		/**
		 * 主交换机类型;为空时取 {@link RabbitMqProperties#exchangeType}.
		 */
		private @Nullable ExchangeType exchangeType;

		/**
		 * 主拓扑是否持久化;为空时取 {@link RabbitMqProperties#durable}.
		 */
		private @Nullable Boolean durable;

		/**
		 * 主拓扑是否自动删除;为空时取 {@link RabbitMqProperties#autoDelete}.
		 */
		private @Nullable Boolean autoDelete;

		/**
		 * 该实体类的重试覆盖配置;为空时全部取 {@link RabbitMqProperties#retry}.
		 */
		private @Nullable Retry retry;

		/**
		 * 该实体类的死信覆盖配置;为空时全部取 {@link RabbitMqProperties#deadLetter}.
		 */
		private @Nullable DeadLetter deadLetter;

		/**
		 * 该实体类的延时覆盖配置;为空时全部取 {@link RabbitMqProperties#delay}.
		 */
		private @Nullable Delay delay;

		/**
		 * 该实体类的监听容器覆盖配置;为空时全部取 {@link RabbitMqProperties#listener}.
		 */
		private @Nullable Listener listener;

	}

	/**
	 * 重试配置,所有字段均可选:实体类级未配置的项回落到全局级,全局级未配置的项回落到内置默认值.
	 */
	@Data
	public static class Retry {

		/**
		 * 总投递次数(含首次);须为正数,{@code 1} 表示不重试,默认 3.
		 */
		private @Nullable Integer attempts;

		/**
		 * 首次重试间隔,默认 {@code 1s}.
		 */
		private @Nullable Duration delay;

		/**
		 * 间隔增长倍率,大于 {@code 1} 时按指数退避,默认 {@code 2.0}.
		 */
		private @Nullable Double multiplier;

		/**
		 * 间隔上限,默认 {@code 30s}.
		 */
		private @Nullable Duration maxDelay;

	}

	/**
	 * 死信拓扑配置,所有字段均可选;开启后主队列通过 {@code x-dead-letter-*} 参数把失败消息投给死信交换机.
	 */
	@Data
	public static class DeadLetter {

		/**
		 * 是否声明死信拓扑,默认 {@code true}.
		 */
		private @Nullable Boolean enabled;

		/**
		 * 死信交换机名称,默认在主交换机名称后追加 {@code .dlx}.
		 */
		private @Nullable String exchange;

		/**
		 * 死信队列名称,默认在主队列名称后追加 {@code .dlq}.
		 */
		private @Nullable String queue;

		/**
		 * 死信路由键,默认在主路由键后追加 {@code .dead}.
		 */
		private @Nullable String routingKey;

	}

	/**
	 * 延时拓扑配置,所有字段均可选;开启后消息先投到延时队列,TTL 到期后由死信机制回投主交换机.
	 */
	@Data
	public static class Delay {

		/**
		 * 是否声明延时拓扑,默认 {@code false}.
		 */
		private @Nullable Boolean enabled;

		/**
		 * 延时交换机名称,默认在主交换机名称后追加 {@code .delay}.
		 */
		private @Nullable String exchange;

		/**
		 * 延时队列名称,默认在主队列名称后追加 {@code .delay}.
		 */
		private @Nullable String queue;

		/**
		 * 延时路由键,默认在主路由键后追加 {@code .delay}.
		 */
		private @Nullable String routingKey;

		/**
		 * 延时队列的消息存活时间,到期后按死信规则回投主交换机,默认 {@code 30s}.
		 */
		private @Nullable Duration ttl;

	}

	/**
	 * 监听容器配置,所有字段均可选.
	 */
	@Data
	public static class Listener {

		/**
		 * 并发消费者数,默认 1.
		 */
		private @Nullable Integer concurrency;

		/**
		 * 每个消费者的预取数量,默认 1.
		 */
		private @Nullable Integer prefetch;

		/**
		 * 失败消息是否重回队列,默认 {@code false};置 {@code false} 才会走死信链路.
		 */
		private @Nullable Boolean requeueRejected;

		/**
		 * 确认模式,默认 {@link AcknowledgeMode#MANUAL}.
		 */
		private @Nullable AcknowledgeMode acknowledgeMode;

	}

	/**
	 * 解析后的完整拓扑定义:所有字段都必定有值,未配置项一律落到内置默认值.
	 *
	 * @param exchange 主交换机名称
	 * @param exchangeType 主交换机类型
	 * @param durable 主拓扑是否持久化
	 * @param autoDelete 主拓扑是否自动删除
	 * @param queue 主队列名称
	 * @param routingKey 主绑定路由键
	 * @param deadLetterEnabled 是否声明死信拓扑
	 * @param deadLetterExchange 死信交换机名称
	 * @param deadLetterQueue 死信队列名称
	 * @param deadLetterRoutingKey 死信路由键
	 * @param delayEnabled 是否声明延时拓扑
	 * @param delayExchange 延时交换机名称
	 * @param delayQueue 延时队列名称
	 * @param delayRoutingKey 延时路由键
	 * @param delayTtl 延时队列的消息存活时间
	 * @param attempts 总投递次数(含首次)
	 * @param retryDelay 首次重试间隔
	 * @param retryMultiplier 间隔增长倍率
	 * @param retryMaxDelay 间隔上限
	 * @param concurrency 监听容器并发消费者数
	 * @param prefetch 监听容器预取数量
	 * @param requeueRejected 监听容器失败后是否重回队列
	 * @param acknowledgeMode 监听容器确认模式
	 */
	public record EntityDefinition(String exchange, ExchangeType exchangeType, boolean durable, boolean autoDelete,
			String queue, String routingKey, boolean deadLetterEnabled, String deadLetterExchange,
			String deadLetterQueue, String deadLetterRoutingKey, boolean delayEnabled, String delayExchange,
			String delayQueue, String delayRoutingKey, Duration delayTtl, int attempts, Duration retryDelay,
			double retryMultiplier, Duration retryMaxDelay, int concurrency, int prefetch, boolean requeueRejected,
			AcknowledgeMode acknowledgeMode) {
	}

}
