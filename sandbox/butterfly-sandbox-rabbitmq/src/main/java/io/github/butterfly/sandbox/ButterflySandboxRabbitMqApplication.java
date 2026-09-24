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

import io.github.butterfly.rabbitmq.autoconfigure.EnableRabbitMqTemplates;
import io.github.butterfly.sandbox.model.Computer;
import io.github.butterfly.sandbox.model.TimeModuleBean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * RabbitMQ 沙箱:只保留与 RabbitMQ 有关的演示,启动只需要一个可用的 RabbitMQ Broker.
 * <p>
 * {@code @EnableRabbitMqTemplates} 按实体类声明交换机、队列与绑定,并为每个实体类注册监听容器工厂(带重试与死信); 消费端见
 * {@code io.github.butterfly.sandbox.rabbitmq.RabbitMqComputerConsumer}。Broker 地址与拓扑参数见
 * {@code application.yml},沙箱测试自己在 {@code @SpringBootTest#properties} 里带连接信息。
 */
@SpringBootApplication
@EnableRabbitMqTemplates({ Computer.class, TimeModuleBean.class })
public class ButterflySandboxRabbitMqApplication {

	public static void main(String[] args) {
		SpringApplication.run(ButterflySandboxRabbitMqApplication.class, args);
	}

}
