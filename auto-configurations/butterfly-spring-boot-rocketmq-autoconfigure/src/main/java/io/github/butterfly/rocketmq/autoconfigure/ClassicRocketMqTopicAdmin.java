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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.remoting.RPCHook;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.util.ClassUtils;

import java.util.HashMap;

/**
 * 用 RocketMQ 老版 remoting 客户端({@code rocketmq-client})的
 * {@link DefaultMQProducer#createTopic} 真正创建主主题.
 * <p>
 * 之所以绕道老客户端,是因为 RocketMQ 5 的 gRPC 客户端(rocketmq-client-java)只负责收发,没有开放任何主题管理
 * API;而老客户端的管理接口针对的是 NameServer,不依赖 gRPC 代理,因此它只能用于"直连 NameServer"的部署形态。
 * <p>
 * 该实现由 {@code ButterflyRocketMqAutoConfiguration} 在满足下面两个条件时注册:类路径上存在
 * {@code DefaultMQProducer},且配置了 {@code rocketmq.name-server}。
 * <p>
 * <b>ACL</b>:开启了 ACL 的 Broker 要求每个请求都带签名,因此成对配置 {@code rocketmq.producer.access-key} 与
 * {@code rocketmq.producer.secret-key} 时,会给管理客户端装上 {@code AclClientRPCHook}。凭据刻意复用 gRPC
 * 生产者那一对,同一套密钥不必配两遍。
 * <p>
 * <b>注意</b>:RocketMQ 5 已经把整个老版 remoting 客户端({@link DefaultMQProducer} 在内的
 * {@code org.apache.rocketmq.client} 一族)标记为已过时,但它是目前唯一能在程序里建主题的入口,所以这里照旧使用,编译期的
 * deprecation 警告是预期内的。不想依赖老客户端时,自行注册一个 {@link RocketMqTopicAdmin} Bean 即可。
 * <p>
 * {@link DefaultMQProducer} 按需惰性创建:没有实体类需要建主题时,连客户端都不会启动;容器关闭时再统一 {@code shutdown}。
 */
class ClassicRocketMqTopicAdmin implements RocketMqTopicAdmin, DisposableBean {

	private static final Log logger = LogFactory.getLog(ClassicRocketMqTopicAdmin.class);

	/**
	 * 建主题用的生产者组名;管理动作不属于任何业务消费链路,固定一个专用组名即可.
	 */
	private static final String ADMIN_PRODUCER_GROUP = "butterfly-rocketmq-topic-admin";

	/**
	 * ACL 钩子类名;{@code rocketmq-acl} 是 optional 依赖,用类名判存在性才不会因它缺席而加载失败.
	 */
	private static final String ACL_HOOK_CLASS_NAME = "org.apache.rocketmq.acl.common.AclClientRPCHook";

	private final String nameServer;

	private final @Nullable String accessKey;

	private final @Nullable String secretKey;

	private @Nullable DefaultMQProducer producer;

	ClassicRocketMqTopicAdmin(String nameServer, @Nullable String accessKey, @Nullable String secretKey) {
		this.nameServer = nameServer;
		this.accessKey = accessKey;
		this.secretKey = secretKey;
	}

	@Override
	public void createTopic(String topic, int queueCount) {
		DefaultMQProducer client = producer();
		try {
			// 第一个参数不是 ACL 的 access key:MQAdminImpl#createTopic 会拿它去做
			// getTopicRouteInfoFromNameServer 找 Broker,传 null 只能得到
			// "No topic route info in name server for the topic: null"。
			// 老客户端的约定是用默认主题 TBW102(即 producer 的 create-topic-key)完成这次路由发现。
			client.createTopic(client.getCreateTopicKey(), topic, queueCount, new HashMap<>());
			logger.info("Created RocketMQ topic '" + topic + "' with " + queueCount + " queues");
		}
		catch (MQClientException ex) {
			throw new IllegalStateException("Failed to create RocketMQ topic '" + topic + "' on " + this.nameServer,
					ex);
		}
	}

	/**
	 * 惰性创建并启动建主题用的生产者;只在第一次真正需要建主题时启动客户端.
	 * @return 已启动的生产者
	 */
	private synchronized DefaultMQProducer producer() {
		DefaultMQProducer existing = this.producer;
		if (existing != null) {
			return existing;
		}
		DefaultMQProducer created = buildProducer();
		try {
			created.start();
		}
		catch (MQClientException ex) {
			throw new IllegalStateException(
					"Failed to start the RocketMQ topic admin producer against " + this.nameServer, ex);
		}
		this.producer = created;
		return created;
	}

	private DefaultMQProducer buildProducer() {
		RPCHook hook = rpcHook();
		DefaultMQProducer created = (hook != null) ? new DefaultMQProducer(ADMIN_PRODUCER_GROUP, hook)
				: new DefaultMQProducer(ADMIN_PRODUCER_GROUP);
		created.setNamesrvAddr(this.nameServer);
		return created;
	}

	/**
	 * 成对配置了访问密钥、且类路径上确实有 {@code rocketmq-acl} 时,构造 ACL 签名钩子.
	 * <p>
	 * 只配了一半、或只有 gRPC 客户端而没有 {@code rocketmq-acl} 时返回 {@code null},按"Broker 未开 ACL"处理:此时
	 * 请求能发出去 但不带签名,真开了 ACL 的 Broker 会自己拒绝并报出清晰的错误,比在这里硬造一个空签名的钩子更好排查。
	 * @return ACL 钩子,不需要签名时为 {@code null}
	 */
	private @Nullable RPCHook rpcHook() {
		String accessKey = this.accessKey;
		String secretKey = this.secretKey;
		if (accessKey == null || accessKey.isEmpty() || secretKey == null || secretKey.isEmpty()) {
			return null;
		}
		if (!ClassUtils.isPresent(ACL_HOOK_CLASS_NAME, getClass().getClassLoader())) {
			logger.warn("RocketMQ access key is configured but 'rocketmq-acl' is not on the classpath; "
					+ "topic creation requests will be sent without an ACL signature");
			return null;
		}
		return AclRpcHookFactory.create(accessKey, secretKey);
	}

	@Override
	public synchronized void destroy() {
		DefaultMQProducer existing = this.producer;
		this.producer = null;
		if (existing != null) {
			existing.shutdown();
		}
	}

}
