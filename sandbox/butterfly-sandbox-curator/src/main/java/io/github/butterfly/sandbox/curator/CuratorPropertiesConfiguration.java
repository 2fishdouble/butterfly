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

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * curator 沙箱的配置绑定:把 {@code butterfly.curator.*} 绑成容器里的 {@link CuratorProperties}.
 * <p>
 * 单独拆出来是因为「绑定配置」与「建客户端」的生命周期不同:即使 {@code butterfly.curator.auto-startup=false}
 * 关掉了客户端({@link CuratorClientConfiguration}),{@link CuratorZookeeperService} 仍需要读到
 * {@code connect-string}、{@code base-path} 这些配置,测试也据此验证绑定与路径拼装。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CuratorProperties.class)
public class CuratorPropertiesConfiguration {

}
