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

import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.ArrayUtil;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;

/**
 * SpEL 表达式解析辅助,用于把注解中声明的多个 SpEL 表达式片段求值并拼接成一个字符串.
 * <p>
 * 典型用途是 Redis 幂等锁的 key 生成:调用方把解析结果与方法全名拼接后作为锁的 key。 求值上下文基于目标方法构建,因此表达式可以引用方法参数名以及容器中的
 * {@code @beanName}; 内部的判空依赖 hutool 的 {@code Validator}。
 * <p>
 * 由 {@link ButterflyAutoConfiguration#spelSup(BeanFactory)} 以 {@code @Bean} 方式注册。
 */
public class SpelSup {

	private static final SpelExpressionParser PARSER = new SpelExpressionParser();

	private static final DefaultParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

	private final BeanResolver beanResolver;

	/**
	 * 创建 SpEL 解析辅助实例.
	 * @param beanFactory 用于把表达式中的 {@code @beanName} 解析为容器内 Bean; 会被包装成
	 * {@link BeanFactoryResolver},因此不能为 {@code null}
	 */
	public SpelSup(BeanFactory beanFactory) {
		this.beanResolver = new BeanFactoryResolver(beanFactory);
	}

	/**
	 * 逐个求值 SpEL 表达式,并把结果按数组顺序用 {@code .} 连接成一个字符串.
	 * <p>
	 * 求值上下文由目标方法构建({@link MethodBasedEvaluationContext}),参数名通过
	 * {@link DefaultParameterNameDiscoverer} 解析,并支持 {@code @beanName} 形式的 Bean 引用;
	 * 每个表达式的求值结果都按 {@link String} 取出。
	 * <p>
	 * 值为 {@code null} 或空字符串(以及 {@code keys} 为 {@code null} 或空数组)的片段会被跳过,
	 * 且不在其后追加分隔符;分隔符只在成功求值的片段且其下标不是数组最后一个时追加, 因此当数组末尾的片段被跳过时,结果可能以 {@code .} 结尾。求值结果为
	 * {@code null} 的表达式会 以字符串 {@code null} 的形式拼入(取决于 SpEL 的转换结果)。
	 * @param method 目标方法,用于发现参数名并作为表达式求值的方法上下文
	 * @param keys 待求值的 SpEL 表达式数组,可为 {@code null};元素可为 {@code null} 或空字符串
	 * @param args 与 {@code method} 对应的实参数组,可为 {@code null},此时引用方法参数的表达式会求值失败
	 * @return 拼接后的字符串;没有任何有效片段时返回空字符串
	 * @throws IllegalArgumentException 任一表达式解析或求值失败时抛出,原始异常作为 cause 保留
	 */
	public String parseSpel(Method method, @Nullable String @Nullable [] keys, Object[] args) {
		StringBuilder sbu = new StringBuilder();
		try {
			if (!ArrayUtil.isEmpty(keys)) {
				StandardEvaluationContext context = new MethodBasedEvaluationContext(null, method, args,
						NAME_DISCOVERER);
				context.setBeanResolver(this.beanResolver);
				for (int i = 0; i < keys.length; i++) {
					String value = keys[i];
					// Validator 未标注可空性,这里显式判空,后续 parseExpression 才能完成空值收窄
					if (value != null && Validator.isNotEmpty(value)) {
						String parseValue = PARSER.parseExpression(value).getValue(context, String.class);
						sbu.append(parseValue);
						if (i < keys.length - 1) {
							sbu.append(".");
						}
					}
				}
			}
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Parse SpEL failed", ex);
		}
		return sbu.toString();
	}

}
