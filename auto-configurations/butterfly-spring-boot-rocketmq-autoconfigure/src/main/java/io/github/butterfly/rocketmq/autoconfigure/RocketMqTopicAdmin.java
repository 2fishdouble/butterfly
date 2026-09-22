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

/**
 * 主题管理扩展点:RocketMQ 5 的 gRPC 客户端(rocketmq-client-java)没有开放任何管理 API.
 * <p>
 * 因此"建主题"这一步必须由使用方按自己的部署形态决定怎么做,本接口就是那个可插拔的位置: {@link RocketMqTopicInitializer}
 * 在启动时对每个实体类的主主题调用一次 {@link #createTopic(String, int)}。
 * <p>
 * 容器中没有自定义实现时,自动配置会注册 {@link LoggingRocketMqTopicAdmin}(只记日志、不建主题);类路径上存在 RocketMQ 老版
 * remoting 客户端且配置了 {@code rocketmq.name-server} 时,则会注册
 * {@link ClassicRocketMqTopicAdmin}(用 {@code DefaultMQProducer} 真正建主题)。
 * <p>
 * 死信主题与重试主题<b>不会</b>走到这里:{@code %DLQ%<消费组>} 与 {@code %RETRY%<消费组>} 是 RocketMQ
 * 的系统主题,Broker 会自行创建,通过管理 API 显式创建反而会被拒绝。
 */
@FunctionalInterface
public interface RocketMqTopicAdmin {

	/**
	 * 创建一个主题;已存在时按具体实现决定是幂等跳过还是抛出异常.
	 * @param topic 主题名
	 * @param queueCount 队列(分区)数
	 */
	void createTopic(String topic, int queueCount);

}
