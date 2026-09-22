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

package io.github.butterfly.boot;

import io.github.butterfly.autoconfigure.BaseEnumJsonFactory;
import io.github.butterfly.autoconfigure.ButterflyAutoConfiguration;
import io.github.butterfly.autoconfigure.HandleExceptionAdvice;
import io.github.butterfly.autoconfigure.SpelSup;
import io.github.butterfly.autoconfigure.TraceIdFilter;
import io.github.butterfly.autoconfigure.WebProperties;
import io.github.butterfly.core.BaseEnum;
import io.github.butterfly.core.R;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.core.Ordered;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ButterflySpringBootAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ButterflyAutoConfiguration.class));

	private final ApplicationContextRunner jacksonContextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, ButterflyAutoConfiguration.class));

	private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(HandleExceptionAdvice.class, ButterflyAutoConfiguration.class));

	/**
	 * classpath 上存在 hutool 时注册 SpelSup.
	 */
	@Test
	void registersSpelSupWhenHutoolIsPresent() {
		this.contextRunner.run((context) -> assertThat(context).hasSingleBean(SpelSup.class));
	}

	/**
	 * hutool 缺失(hutool 为可选依赖)时跳过 SpelSup.
	 */
	@Test
	void skipsSpelSupWhenHutoolIsMissing() {
		this.contextRunner.withClassLoader(new FilteredClassLoader("cn.hutool.core.lang.Validator"))
			.run((context) -> assertThat(context).doesNotHaveBean(SpelSup.class));
	}

	/**
	 * 使用方自定义 SpelSup 时以使用方的为准.
	 */
	@Test
	void backsOffSpelSupWhenUserDefinesBean() {
		SpelSup spelSup = new SpelSup(new DefaultListableBeanFactory());
		this.contextRunner.withBean(SpelSup.class, () -> spelSup)
			.run((context) -> assertThat(context.getBean(SpelSup.class)).isSameAs(spelSup));
	}

	/**
	 * SpelSup 按顺序求值并用 {@code .} 拼接,支持方法参数名与 {@code @beanName} 引用.
	 */
	@Test
	void spelSupResolvesArgumentsAndBeans() throws Exception {
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
		beanFactory.registerSingleton("suffix", "order");
		SpelSup spelSup = new SpelSup(beanFactory);
		Method method = SampleController.class.getDeclaredMethod("handle", String.class, int.class);

		assertThat(
				spelSup.parseSpel(method, new String[] { "#name", "#age", "@suffix" }, new Object[] { "butterfly", 3 }))
			.isEqualTo("butterfly.3.order");
	}

	/**
	 * 数组中的空片段被跳过,且不会留下连续分隔符.
	 */
	@Test
	void spelSupSkipsEmptyKeys() throws Exception {
		SpelSup spelSup = new SpelSup(new DefaultListableBeanFactory());
		Method method = SampleController.class.getDeclaredMethod("handle", String.class, int.class);
		// 元素类型显式声明为可空:数组字面量在 @NullMarked 下默认是非空元素
		@Nullable String[] keys = { "#name", "", null, "#age" };

		assertThat(spelSup.parseSpel(method, keys, new Object[] { "butterfly", 3 })).isEqualTo("butterfly.3");
		assertThat(spelSup.parseSpel(method, null, new Object[] { "butterfly", 3 })).isEmpty();
		assertThat(spelSup.parseSpel(method, new String[0], new Object[] { "butterfly", 3 })).isEmpty();
	}

	/**
	 * 表达式非法时抛出 IllegalArgumentException,并保留原始异常.
	 */
	@Test
	void spelSupWrapsParseFailure() throws Exception {
		SpelSup spelSup = new SpelSup(new DefaultListableBeanFactory());
		Method method = SampleController.class.getDeclaredMethod("handle", String.class, int.class);

		assertThatIllegalArgumentException()
			.isThrownBy(() -> spelSup.parseSpel(method, new String[] { "#[" }, new Object[] { "butterfly", 3 }))
			.withMessage("Parse SpEL failed")
			.withCauseInstanceOf(Exception.class);
	}

	/**
	 * 默认注册 JsonMapper 定制器.
	 */
	@Test
	void registersJsonMapperBuilderCustomizer() {
		this.contextRunner.run((context) -> assertThat(context).hasSingleBean(JsonMapperBuilderCustomizer.class));
	}

	/**
	 * 使用方自定义定制器时不再注册默认定制器.
	 */
	@Test
	void backsOffJsonMapperBuilderCustomizerWhenUserDefinesBean() {
		JsonMapperBuilderCustomizer customizer = (builder) -> {
		};
		this.contextRunner.withBean(JsonMapperBuilderCustomizer.class, () -> customizer).run((context) -> {
			assertThat(context).hasSingleBean(JsonMapperBuilderCustomizer.class);
			assertThat(context.getBean(JsonMapperBuilderCustomizer.class)).isSameAs(customizer);
		});
	}

	/**
	 * 定制后的 JsonMapper:BaseEnum 序列化为 code,Long 序列化为字符串,时间按 PatternConstant 格式收发.
	 */
	@Test
	void customizesJsonMapperForBaseEnumLongAndDateTime() {
		this.jacksonContextRunner.run((context) -> {
			JsonMapper mapper = context.getBean(JsonMapper.class);

			assertThat(mapper.writeValueAsString(SampleEnum.FIRST)).isEqualTo("1");
			assertThat(mapper.writeValueAsString(SampleEnum.SECOND)).isEqualTo("2");

			assertThat(mapper.writeValueAsString(9007199254740993L)).isEqualTo("\"9007199254740993\"");
			assertThat(mapper.readValue("\"9007199254740993\"", Long.class)).isEqualTo(9007199254740993L);
			assertThat(mapper.readValue("9007199254740993", Long.class)).isEqualTo(9007199254740993L);

			assertThat(mapper.writeValueAsString(LocalDateTime.of(2024, 1, 2, 3, 4, 5)))
				.isEqualTo("\"2024-01-02 03:04:05\"");
			assertThat(mapper.readValue("\"2024-01-02 03:04:05\"", LocalDateTime.class))
				.isEqualTo(LocalDateTime.of(2024, 1, 2, 3, 4, 5));
			assertThat(mapper.writeValueAsString(LocalDate.of(2024, 1, 2))).isEqualTo("\"2024-01-02\"");
			assertThat(mapper.readValue("\"2024-01-02\"", LocalDate.class)).isEqualTo(LocalDate.of(2024, 1, 2));
			assertThat(mapper.writeValueAsString(LocalTime.of(3, 4, 5))).isEqualTo("\"03:04:05\"");
			assertThat(mapper.readValue("\"03:04:05\"", LocalTime.class)).isEqualTo(LocalTime.of(3, 4, 5));
		});
	}

	/**
	 * BaseEnum 的解析规则:依次按 code、title、枚举常量名(忽略大小写)匹配,空值直接返回 null.
	 */
	@Test
	void parsesBaseEnumFromCodeTitleOrConstantName() {
		assertThat(BaseEnumJsonFactory.<SampleEnum>parse(SampleEnum.class, 1)).isEqualTo(SampleEnum.FIRST);
		assertThat(BaseEnumJsonFactory.<SampleEnum>parse(SampleEnum.class, "1")).isEqualTo(SampleEnum.FIRST);
		assertThat(BaseEnumJsonFactory.<SampleEnum>parse(SampleEnum.class, "第一")).isEqualTo(SampleEnum.FIRST);
		assertThat(BaseEnumJsonFactory.<SampleEnum>parse(SampleEnum.class, "first")).isEqualTo(SampleEnum.FIRST);
		assertThat(BaseEnumJsonFactory.<SampleEnum>parse(SampleEnum.class, "SECOND")).isEqualTo(SampleEnum.SECOND);
		assertThat(BaseEnumJsonFactory.<SampleEnum>parse(SampleEnum.class, " second ")).isEqualTo(SampleEnum.SECOND);
		assertThat(BaseEnumJsonFactory.<SampleEnum>parse(SampleEnum.class, null)).isNull();
		assertThat(BaseEnumJsonFactory.<SampleEnum>parse(SampleEnum.class, "  ")).isNull();
	}

	/**
	 * 无法匹配任何 code/title/常量名时抛出 IllegalArgumentException,而不是静默返回 null.
	 */
	@Test
	void failsToParseUnknownBaseEnumValue() {
		assertThatIllegalArgumentException().isThrownBy(() -> BaseEnumJsonFactory.parse(SampleEnum.class, "unknown"))
			.withMessageContaining("unknown");
	}

	/**
	 * 默认注册 TraceId 过滤器,且优先级最高.
	 */
	@Test
	void registersTraceIdFilterByDefault() {
		this.contextRunner.run((context) -> {
			assertThat(context).hasSingleBean(TraceIdFilter.class);
			assertThat(context.getBean(TraceIdFilter.class).getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
		});
	}

	/**
	 * WebProperties 的默认值.
	 */
	@Test
	void bindsWebPropertiesDefaults() {
		this.contextRunner.run((context) -> {
			WebProperties properties = context.getBean(WebProperties.class);
			assertThat(properties.getTraceId().isEnabled()).isFalse();
			assertThat(properties.getTraceId().getHeader()).isEqualTo("X-Trace-Id");
		});
	}

	/**
	 * traceId 请求头名称可配置.
	 */
	@Test
	void bindsTraceIdHeaderFromProperties() {
		this.contextRunner.withPropertyValues("butterfly.web.trace-id.header=X-Custom-Trace-Id").run((context) -> {
			assertThat(context.getBean(WebProperties.class).getTraceId().getHeader()).isEqualTo("X-Custom-Trace-Id");
			assertThat(context.getBean(TraceIdFilter.class)).isNotNull();
		});
	}

	/**
	 * 关闭后不注册 TraceId 过滤器.
	 */
	@Test
	void skipsTraceIdFilterWhenDisabled() {
		this.contextRunner.withPropertyValues("butterfly.web.trace-id.enabled=false")
			.run((context) -> assertThat(context).doesNotHaveBean(TraceIdFilter.class));
	}

	/**
	 * 使用方自定义 TraceIdFilter 时以使用方的为准.
	 */
	@Test
	void backsOffTraceIdFilterWhenUserDefinesBean() {
		TraceIdFilter filter = new TraceIdFilter("X-User-Trace-Id");
		this.contextRunner.withBean(TraceIdFilter.class, () -> filter)
			.run((context) -> assertThat(context.getBean(TraceIdFilter.class)).isSameAs(filter));
	}

	/**
	 * 请求头已有 traceId 时透传(去空白),写入 MDC 与响应头,并在结束后清理 MDC.
	 */
	@Test
	void traceIdFilterReusesIncomingHeader() throws Exception {
		TraceIdFilter filter = new TraceIdFilter("X-Trace-Id");
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
		request.addHeader("X-Trace-Id", "  abc123  ");
		MockHttpServletResponse response = new MockHttpServletResponse();
		AtomicReference<@Nullable String> traceIdInChain = new AtomicReference<>();

		filter.doFilter(request, response, (req, res) -> traceIdInChain.set(MDC.get("traceId")));

		assertThat(traceIdInChain.get()).isEqualTo("abc123");
		assertThat(response.getHeader("X-Trace-Id")).isEqualTo("abc123");
		assertThat(MDC.get("traceId")).isNull();
	}

	/**
	 * 请求头没有 traceId 时生成 16 位 traceId.
	 */
	@Test
	void traceIdFilterGeneratesTraceIdWhenHeaderIsMissing() throws Exception {
		TraceIdFilter filter = new TraceIdFilter("X-Trace-Id");
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, (req, res) -> {
		});

		assertThat(response.getHeader("X-Trace-Id")).hasSize(16);
		assertThat(MDC.get("traceId")).isNull();
	}

	/**
	 * Servlet Web 应用中注册全局异常兜底处理器.
	 */
	@Test
	void registersHandleExceptionAdviceInServletWebApplication() {
		this.webContextRunner.run((context) -> assertThat(context).hasSingleBean(HandleExceptionAdvice.class));
	}

	/**
	 * 非 Web 应用中不注册全局异常兜底处理器.
	 */
	@Test
	void skipsHandleExceptionAdviceOutsideServletWebApplication() {
		this.contextRunner.run((context) -> assertThat(context).doesNotHaveBean(HandleExceptionAdvice.class));
	}

	/**
	 * 兜底处理器把异常包装成统一失败响应.
	 */
	@Test
	void handleExceptionAdviceWrapsThrowableAsErrorResponse() {
		R<Object> result = new HandleExceptionAdvice().handleThrowable(new IllegalStateException("boom"));

		assertThat(result.getCode()).isEqualTo(100000);
		assertThat(result.getIsSuccess()).isFalse();
		assertThat(result.getMsg()).isEqualTo("boom");
		assertThat(result.getData()).isNull();
	}

	static class SampleController {

		void handle(String name, int age) {
		}

	}

	enum SampleEnum implements BaseEnum {

		FIRST(1, "第一"),

		SECOND(2, "第二");

		private final int code;

		private final String title;

		SampleEnum(int code, String title) {
			this.code = code;
			this.title = title;
		}

		@Override
		public int getCode() {
			return this.code;
		}

		@Override
		public String getTitle() {
			return this.title;
		}

	}

}
