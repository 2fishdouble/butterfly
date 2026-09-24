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

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * RocketMQ 沙箱:只保留与 RocketMQ 有关的演示,启动只需要一个可用的 RocketMQ NameServer.
 * <p>
 * 发送端用 {@code RocketMQTemplate},消费端见
 * {@code io.github.butterfly.sandbox.rocketmq.RocketmqComputerConsumer}
 * (顺序消费、标签过滤、异常重试各一个消费者)。NameServer 与 ACL 配置见 {@code application.yml},对应
 * {@code rocketmq.*} 前缀。
 */
@SpringBootApplication
public class ButterflySandboxRocketMqApplication {

	public static void main(String[] args) {
		SpringApplication.run(ButterflySandboxRocketMqApplication.class, args);
	}

}
