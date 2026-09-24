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
 * canal 沙箱:演示直连 canal server 消费 MySQL binlog,把 INSERT、UPDATE、DELETE 事件路由到处理器.
 * <p>
 * 两种写法各有一个示例,都被自动注册,可以同时收到事件:
 * <ul>
 * <li>泛型驱动:{@code io.github.butterfly.sandbox.canal.ComputerRowHandler},表名由实体类简单名推出;</li>
 * <li>注解驱动:{@code io.github.butterfly.sandbox.canal.ComputerListener},在方法上标注
 * {@code @CanalListener}。</li>
 * </ul>
 * canal server 地址、实例名与订阅表达式见 {@code application.yml} 的 {@code butterfly.canal.*};应用启动后即开始
 * 消费({@code auto-startup=true}),沙箱测试则用 {@code auto-startup=false} 只验证装配与分发,不连 canal。
 */
@SpringBootApplication
public class ButterflySandboxCanalApplication {

	public static void main(String[] args) {
		SpringApplication.run(ButterflySandboxCanalApplication.class, args);
	}

}
