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

import io.github.butterfly.kafka.autoconfigure.EnableKafkaTemplates;
import io.github.butterfly.sandbox.model.Computer;
import io.github.butterfly.sandbox.model.TimeModuleBean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Kafka 沙箱:只保留与 Kafka 有关的演示,启动只需要一个可用的 Kafka Broker.
 * <p>
 * {@code @EnableKafkaTemplates} 按实体类注册专用的 {@code KafkaTemplate}、主主题以及重试与死信主题;消费端见
 * {@code io.github.butterfly.sandbox.kafka.KafkaComputerConsumer}。Broker 地址与主题参数见
 * {@code application.yml},沙箱测试自己在 {@code @SpringBootTest#properties} 里带连接信息,因此不依赖本文件里的地址。
 */
@SpringBootApplication
@EnableKafkaTemplates({ Computer.class, TimeModuleBean.class })
public class ButterflySandboxKafkaApplication {

	public static void main(String[] args) {
		SpringApplication.run(ButterflySandboxKafkaApplication.class, args);
	}

}
