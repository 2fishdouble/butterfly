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

import org.apache.rocketmq.client.core.RocketMQClientTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * RocketMQ 自动配置:注册主题管理器与启动时的建主题动作.
 * <p>
 * 本模块<b>不</b>注册 {@link RocketMQClientTemplate},也不注册生产者/消费者 Builder:这些都由
 * {@code rocketmq-v5-client-spring-boot-starter} 自己的 {@code RocketMQAutoConfiguration} 按
 * {@code rocketmq.producer.*} / {@code rocketmq.simple-consumer.*} 完成。这里只补两件事:
 * <ul>
 * <li>一个 {@link RocketMqTopicAdmin}:类路径上存在老版 remoting 客户端且配置了
 * {@code rocketmq.name-server} 时用 {@link ClassicRocketMqTopicAdmin} 真正建主题,否则用只记日志的
 * {@link LoggingRocketMqTopicAdmin};</li>
 * <li>一个 {@link RocketMqTopicInitializer},在启动末尾把 {@link EnableRocketMqTemplates}
 * 声明的各实体类主主题交给 主题管理器创建。</li>
 * </ul>
 * <p>
 * <b>为什么主题管理器是一个 {@code @Bean} 方法而不是两个带条件的嵌套配置类</b>:嵌套配置类在 Spring 里总是晚于外部类的 {@code @Bean}
 * 方法被解析,两个实现又都带 {@code @ConditionalOnMissingBean},于是兜底实现会先注册、真正建主题的实现
 * 永远没机会生效。把选择收敛到一个方法里,顺序就完全确定了。
 * <p>
 * 无论走哪条分支,使用方自定义 {@link RocketMqTopicAdmin} Bean 时本配置都退让。
 */
@AutoConfiguration
@ConditionalOnClass(RocketMQClientTemplate.class)
public class ButterflyRocketMqAutoConfiguration {

	/**
	 * 老版 remoting 客户端的建主题入口;用类名字符串判存在性,缺席时本类也不会因它而加载失败.
	 */
	private static final String CLASSIC_PRODUCER_CLASS_NAME = "org.apache.rocketmq.client.producer.DefaultMQProducer";

	/**
	 * NameServer 地址属性;老版 remoting 客户端只能直连 NameServer,gRPC 代理式的部署没有这个属性.
	 */
	private static final String NAME_SERVER_PROPERTY = "rocketmq.name-server";

	/**
	 * ACL 访问密钥属性;管理客户端与 gRPC 生产者共用同一对凭据,不必配两遍.
	 */
	private static final String ACCESS_KEY_PROPERTY = "rocketmq.producer.access-key";

	/**
	 * ACL 密钥属性;与 {@value #ACCESS_KEY_PROPERTY} 成对出现.
	 */
	private static final String SECRET_KEY_PROPERTY = "rocketmq.producer.secret-key";

	/**
	 * 注册主题管理器.
	 * <p>
	 * 只有"配置了 NameServer 且类路径上确实有老版 remoting 客户端"时才真去建主题:两个条件缺一个都退回只记日志的实现, 因为 RocketMQ 5
	 * 的 gRPC 客户端没有任何管理 API,硬调只会得到连接错误而不是建好的主题。
	 * @param environment 用于读取 {@code rocketmq.name-server} 与 ACL 凭据
	 * @return 主题管理器
	 */
	@Bean
	@ConditionalOnMissingBean(RocketMqTopicAdmin.class)
	RocketMqTopicAdmin butterflyRocketMqTopicAdmin(Environment environment) {
		String nameServer = environment.getProperty(NAME_SERVER_PROPERTY);
		if (StringUtils.hasText(nameServer) && ClassUtils.isPresent(CLASSIC_PRODUCER_CLASS_NAME,
				ButterflyRocketMqAutoConfiguration.class.getClassLoader())) {
			return new ClassicRocketMqTopicAdmin(nameServer, environment.getProperty(ACCESS_KEY_PROPERTY),
					environment.getProperty(SECRET_KEY_PROPERTY));
		}
		return new LoggingRocketMqTopicAdmin();
	}

	/**
	 * 注册启动时的建主题动作.
	 * <p>
	 * 主题定义来自 {@link RocketMqRegistrar};没有标注 {@link EnableRocketMqTemplates} 时集合为空,本 Bean
	 * 什么也不做。
	 * @param definitions 按实体类注册的主题定义
	 * @param topicAdmin 容器中的主题管理器
	 * @return 主题初始化器
	 */
	@Bean
	RocketMqTopicInitializer butterflyRocketMqTopicInitializer(
			ObjectProvider<RocketMqProperties.TopicDefinition> definitions, RocketMqTopicAdmin topicAdmin) {
		return new RocketMqTopicInitializer(topicAdmin, definitions.orderedStream().toList());
	}

}
