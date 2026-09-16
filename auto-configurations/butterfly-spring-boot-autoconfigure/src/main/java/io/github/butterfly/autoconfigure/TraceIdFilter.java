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

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 请求日志与 TraceId 过滤器.
 *
 * <p>
 * 为每次请求生成/透传 traceId，写入 MDC 供日志输出（建议在日志 pattern 中配置
 * {@code %X{traceId}}），并回写到响应头便于调用方串联链路；同时打印请求开始/结束耗时日志。
 */
public class TraceIdFilter extends OncePerRequestFilter implements Ordered {

	/** 日志对象. */
	private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);

	/** MDC 中 traceId 的键名. */
	private static final String TRACE_ID_MDC_KEY = "traceId";

	/** 日志中展示的 traceId 长度. */
	private static final int TRACE_ID_LENGTH = 16;

	/** TraceId 请求头名称. */
	private final String traceIdHeader;

	/**
	 * 构造器.
	 * @param traceIdHeader traceId 请求头名称
	 */
	public TraceIdFilter(String traceIdHeader) {
		this.traceIdHeader = traceIdHeader;
	}

	/**
	 * 过滤器优先级设为最高，保证日志尽早带上 traceId.
	 * @return 排序值
	 */
	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String traceId = resolveTraceId(request);
		MDC.put(TRACE_ID_MDC_KEY, traceId);
		response.setHeader(this.traceIdHeader, traceId);
		long startTime = System.currentTimeMillis();
		log.info("请求开始, method={}, uri={}", request.getMethod(), request.getRequestURI());
		try {
			chain.doFilter(request, response);
		}
		finally {
			log.info("请求结束, method={}, uri={}, status={}, cost={}ms", request.getMethod(), request.getRequestURI(),
					response.getStatus(), System.currentTimeMillis() - startTime);
			MDC.remove(TRACE_ID_MDC_KEY);
		}
	}

	private String resolveTraceId(HttpServletRequest request) {
		String headerValue = request.getHeader(this.traceIdHeader);
		if (headerValue != null && !headerValue.isBlank()) {
			return headerValue.trim();
		}
		return UUID.randomUUID().toString().replace("-", "").substring(0, TRACE_ID_LENGTH);
	}

}
