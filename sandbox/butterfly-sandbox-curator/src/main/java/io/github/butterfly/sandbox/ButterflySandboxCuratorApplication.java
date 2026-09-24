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
 * curator 沙箱:用 Apache Curator 连接 ZooKeeper,并把最简单的增删改查暴露成 HTTP 接口.
 * <p>
 * 启动只需要一个可用的 ZooKeeper:地址见 {@code application.yml} 的
 * {@code butterfly.curator.connect-string}。 客户端 bean 由
 * {@code io.github.butterfly.sandbox.curator.CuratorClientConfiguration} 建出来并异步建连, 读写入口是
 * {@code io.github.butterfly.sandbox.curator.CuratorController}: <pre>
 * curl http://localhost:8080/curator/status
 * curl -X PUT -d hello http://localhost:8080/curator/nodes/computer-1
 * curl http://localhost:8080/curator/nodes/computer-1
 * curl http://localhost:8080/curator/nodes
 * curl -X DELETE http://localhost:8080/curator/nodes/computer-1
 * </pre> 想直接看 ZK 上的结果,可用
 * {@code zkCli.sh -server 192.168.12.29:2181 get /butterfly/sandbox/curator/computer-1}。
 */
@SpringBootApplication
public class ButterflySandboxCuratorApplication {

	public static void main(String[] args) {
		SpringApplication.run(ButterflySandboxCuratorApplication.class, args);
	}

}
