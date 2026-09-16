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

package io.github.butterfly.autoconfigure;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * butterfly-web 自动装配配置项.
 *
 * <p>
 * 配置前缀为 {@code butterfly.web}，各功能默认开启；可通过 application.yaml 灵活启停：
 *
 * <pre>
 * butterfly:
 *   web:
 *     trace-id:
 *       enabled: true
 *       header: X-Trace-Id
 * </pre>
 */
@ConfigurationProperties(prefix = "butterfly.web")
@Getter
public class WebProperties {

	/** 请求日志与 TraceId 配置. */
	private final TraceId traceId = new TraceId();

	/**
	 * 请求日志与 TraceId 配置.
	 */
	@Getter
	@Setter
	public static class TraceId {

		/** 是否启用请求日志与 TraceId 透传. */
		private boolean enabled = false;

		/** TraceId 请求头名称. */
		private String header = "X-Trace-Id";

	}

}
