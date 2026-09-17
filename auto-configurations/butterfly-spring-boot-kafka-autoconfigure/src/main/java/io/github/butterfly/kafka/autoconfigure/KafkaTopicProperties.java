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
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kafka 主题配置,前缀 {@code butterfly.kafka.topic}.
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
 */
@Data
@ConfigurationProperties(prefix = "butterfly.kafka.topic")
public class KafkaTopicProperties {

	/**
	 * 自动创建主题时的默认分区数,可被 {@link #entities} 中的同名配置覆盖.
	 */
	private int partitions = 1;

	/**
	 * 自动创建主题时的默认副本数,可被 {@link #entities} 中的同名配置覆盖.
	 */
	private int replicas = 1;

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
		Entity entity = findEntity(entityClass);

		String name = (entity != null && StringUtils.hasText(entity.getName())) ? entity.getName()
				: uncapitalize(entityClass.getSimpleName());
		int resolvedPartitions = (entity != null && entity.getPartitions() != null) ? entity.getPartitions()
				: this.partitions;
		int resolvedReplicas = (entity != null && entity.getReplicas() != null) ? entity.getReplicas() : this.replicas;

		return new TopicDefinition(name, resolvedPartitions, resolvedReplicas);
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
	 * 单个实体类的主题覆盖配置,三个字段均可选,为空时回落到全局默认值.
	 */
	@Data
	public static class Entity {

		/**
		 * 主题名;为空时取实体类简单名首字母小写.
		 */
		private @Nullable String name;

		/**
		 * 分区数;为空时取 {@link KafkaTopicProperties#getPartitions()}.
		 */
		private @Nullable Integer partitions;

		/**
		 * 副本数;为空时取 {@link KafkaTopicProperties#getReplicas()}.
		 */
		private @Nullable Integer replicas;

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

}
