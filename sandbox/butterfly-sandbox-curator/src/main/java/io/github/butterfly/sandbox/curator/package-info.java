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

/**
 * curator 沙箱核心包.
 * <p>
 * 用 Apache Curator 直连 ZooKeeper,演示四件事:怎么按 {@code butterfly.curator.*} 建出并启动客户端
 * ({@link io.github.butterfly.sandbox.curator.CuratorClientConfiguration})、怎么读写节点
 * ({@link io.github.butterfly.sandbox.curator.CuratorZookeeperService})、怎么判断连接状态,以及怎么把这几步
 * 通过 HTTP 暴露出来便于验证({@link io.github.butterfly.sandbox.curator.CuratorController})。
 */
@NullMarked
package io.github.butterfly.sandbox.curator;

import org.jspecify.annotations.NullMarked;
