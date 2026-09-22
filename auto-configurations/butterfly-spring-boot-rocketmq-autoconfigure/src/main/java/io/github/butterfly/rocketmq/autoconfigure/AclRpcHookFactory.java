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

import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.remoting.RPCHook;

/**
 * ACL 签名钩子的工厂,把对 {@code rocketmq-acl} 的类型引用关在这一个类里.
 * <p>
 * {@code rocketmq-acl} 是本模块的 <b>optional</b> 依赖,只有开启了 ACL 的 Broker 才需要它。把引用集中在这里、并且只在
 * {@code ClassUtils.isPresent} 判定为真之后才加载本类,可以保证 ACL 缺席时 {@link ClassicRocketMqTopicAdmin}
 * 不会因为校验阶段解析不到 {@code AclClientRPCHook} 而抛 {@code NoClassDefFoundError}。
 */
final class AclRpcHookFactory {

	private AclRpcHookFactory() {
	}

	/**
	 * 用访问密钥构造 ACL 签名钩子.
	 * @param accessKey 访问密钥
	 * @param secretKey 密钥
	 * @return 每次请求都带签名的 RPC 钩子
	 */
	static RPCHook create(String accessKey, String secretKey) {
		return new AclClientRPCHook(new SessionCredentials(accessKey, secretKey));
	}

}
