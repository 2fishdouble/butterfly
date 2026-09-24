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

package io.github.butterfly.sandbox.curator;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 把 {@link CuratorZookeeperService} 暴露成 HTTP 接口,用来在没有 {@code zkCli} 的机器上验证连接与读写:
 * <ul>
 * <li>{@code GET /curator/status} —— 连接状态;</li>
 * <li>{@code GET /curator/nodes} —— 列出根路径下的节点;</li>
 * <li>{@code GET /curator/nodes/{name}} —— 读节点值,不存在返回 404;</li>
 * <li>{@code PUT /curator/nodes/{name}} —— 创建或覆盖节点,请求体即节点值,成功返回 204;</li>
 * <li>{@code DELETE /curator/nodes/{name}} —— 删除节点(含子节点),不存在返回 404.</li>
 * </ul>
 * {@code name} 只允许一段节点名(不含 {@code /}),写操作一律落在 {@code butterfly.curator.base-path} 下面,
 * 不会碰到 ZK 上别的应用的数据.
 */
@RestController
@RequestMapping("/curator")
public class CuratorController {

	private final CuratorZookeeperService service;

	public CuratorController(CuratorZookeeperService service) {
		this.service = service;
	}

	/**
	 * 连接状态.
	 * @return 客户端是否已启动、会话是否已建立,以及连接串、命名空间与根路径
	 */
	@GetMapping("/status")
	public Status status() {
		boolean connected = this.service.connected();
		return new Status(this.service.state().name(), connected, describe(connected), this.service.connectString(),
				this.service.namespace(), this.service.basePath());
	}

	/**
	 * 根路径下的节点名.
	 * @return 子节点名列表
	 * @throws Exception 连接不可用
	 */
	@GetMapping("/nodes")
	public List<String> nodes() throws Exception {
		return this.service.children(null);
	}

	/**
	 * 读节点值.
	 * @param name 节点名
	 * @return 节点值,节点不存在时 404
	 * @throws Exception 连接不可用
	 */
	@GetMapping("/nodes/{name}")
	public ResponseEntity<String> read(@PathVariable String name) throws Exception {
		return this.service.read(name).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
	}

	/**
	 * 创建或覆盖节点.
	 * @param name 节点名
	 * @param value 节点值
	 * @return 204
	 * @throws Exception 连接不可用
	 */
	@PutMapping("/nodes/{name}")
	public ResponseEntity<Void> write(@PathVariable String name, @RequestBody @Nullable String value) throws Exception {
		this.service.create(name, (value != null) ? value : "");
		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}

	/**
	 * 删除节点.
	 * @param name 节点名
	 * @return 204;节点本来就不存在时 404
	 * @throws Exception 连接不可用
	 */
	@DeleteMapping("/nodes/{name}")
	public ResponseEntity<Void> delete(@PathVariable String name) throws Exception {
		return this.service.delete(name) ? ResponseEntity.status(HttpStatus.NO_CONTENT).build()
				: ResponseEntity.notFound().build();
	}

	private static String describe(boolean connected) {
		return connected ? "已连上 ZooKeeper" : "尚未建立会话:客户端可能未启动,或 curator 正在重连";
	}

	/**
	 * 连接状态响应.
	 *
	 * @param state 客户端生命周期状态:{@code LATENT} 未启动、{@code STARTED} 已启动、{@code STOPPED} 已关闭
	 * @param connected 会话是否已经建立
	 * @param message 状态的中文说明
	 * @param connectString ZooKeeper 连接串
	 * @param namespace curator 命名空间,未配置时为空串
	 * @param basePath 演示节点根路径
	 */
	public record Status(String state, boolean connected, String message, String connectString, String namespace,
			String basePath) {
	}

}
