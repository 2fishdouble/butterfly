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

package io.github.butterfly.canal.autoconfigure;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * 处理器注册器:扫描容器中的单例 Bean,把两种写法的处理器注册到 {@link CanalEventDispatcher}.
 * <p>
 * 识别两类处理器:
 * <ul>
 * <li>实现 {@link CanalRowHandler} 的 Bean(泛型驱动):泛型参数解析成实体类后,INSERT、UPDATE、DELETE 各注册 一个
 * {@link CanalRowEventHandler};</li>
 * <li>标注 {@link CanalListener} 的方法(注解驱动):{@link CanalListener#events()} 里的每个事件类型各注册一个
 * {@link MethodCanalEventHandler}。</li>
 * </ul>
 * <p>
 * 两类写法可以混用,也可以同时命中同一张表:此时它们都会被调用,顺序为先注册的泛型处理器、后注解方法(取决于容器 中 Bean 名称的扫描顺序)。
 * <p>
 * 注册发生在所有单例 Bean 实例化之后({@link SmartInitializingSingleton}),因此一定早于消费端
 * {@code SmartLifecycle#start()} 开始拉取数据。
 * <p>
 * 表名与库名的解析规则见 {@link CanalTableResolver};泛型参数无法解析或注解方法无法推断表名等配置错误会在启动阶段 直接抛出
 * {@link IllegalStateException},而不是等到第一条数据到达。
 */
public class CanalHandlerRegistrar implements SmartInitializingSingleton {

	private static final Logger log = LoggerFactory.getLogger(CanalHandlerRegistrar.class);

	private final ListableBeanFactory beanFactory;

	private final CanalEventDispatcher dispatcher;

	private final CanalRowMapper rowMapper;

	/**
	 * 创建注册器.
	 * @param beanFactory 用于扫描处理器 Bean
	 * @param dispatcher 处理器注册目标
	 * @param rowMapper 行数据映射器,转交给各处理器适配器
	 */
	public CanalHandlerRegistrar(ListableBeanFactory beanFactory, CanalEventDispatcher dispatcher,
			CanalRowMapper rowMapper) {
		this.beanFactory = beanFactory;
		this.dispatcher = dispatcher;
		this.rowMapper = rowMapper;
	}

	/**
	 * 扫描全部单例 Bean 并注册处理器.
	 * <p>
	 * 只扫描单例,且不使用提前初始化(避免为了判断类型而创建 {@code FactoryBean} 产物)。
	 * @throws IllegalStateException 处理器声明有误时抛出,例如泛型参数无法解析、{@link CanalListener} 方法无法
	 * 推断表名或参数个数超过两个
	 */
	@Override
	public void afterSingletonsInstantiated() {
		for (String beanName : this.beanFactory.getBeanNamesForType(Object.class, false, false)) {
			registerHandlers(beanName);
		}
		log.info("Registered {} canal event handlers", this.dispatcher.handlerCount());
	}

	private void registerHandlers(String beanName) {
		Object bean = this.beanFactory.getBean(beanName);
		Class<?> beanType = AopUtils.getTargetClass(bean);

		if (bean instanceof CanalRowHandler<?> rowHandler) {
			registerRowHandler(beanName, rowHandler, beanType);
		}

		for (Method method : ReflectionUtils.getUniqueDeclaredMethods(beanType)) {
			CanalListener listener = AnnotatedElementUtils.findMergedAnnotation(method, CanalListener.class);
			if (listener != null && !method.isBridge() && !method.isSynthetic()) {
				registerListenerMethod(beanName, bean, method, listener);
			}
		}
	}

	/**
	 * 注册泛型驱动的处理器:泛型参数即实体类,三个事件类型各注册一份.
	 * @param beanName 处理器 Bean 名称,仅用于报错
	 * @param rowHandler 处理器实例
	 * @param beanType 处理器目标类型,用于解析泛型参数
	 * @throws IllegalStateException 泛型参数无法解析成具体类型时抛出
	 */
	private void registerRowHandler(String beanName, CanalRowHandler<?> rowHandler, Class<?> beanType) {
		Class<?> rowType = ResolvableType.forClass(beanType).as(CanalRowHandler.class).getGeneric(0).resolve();
		if (rowType == null || rowType == Object.class) {
			throw new IllegalStateException("Cannot resolve the row type of canal handler '" + beanName + "' ("
					+ beanType.getName() + "); implement CanalRowHandler with a concrete type argument");
		}

		String table = rowHandler.table();
		if (table == null) {
			table = CanalTableResolver.resolveTable(rowType);
		}
		String schema = rowHandler.schema();
		if (schema == null) {
			schema = CanalTableResolver.resolveSchema(rowType);
		}

		for (CanalEventType eventType : CanalEventType.values()) {
			this.dispatcher.register(createRowEventHandler(rowHandler, rowType, table, schema, eventType));
			log.debug("Registered canal row handler '{}' for {} on {}.{}", beanName, eventType, schema, table);
		}
	}

	/**
	 * 把泛型驱动的处理器与解析出的泛型参数装配成事件处理器.
	 * <p>
	 * 泛型参数由 {@link #registerRowHandler} 解析,与这里强转出的 {@code Class<Object>} 必然一致,因此这里的
	 * 未检查转换是安全的。
	 * @param rowHandler 处理器实例
	 * @param rowType 解析出的实体类
	 * @param table 目标表名
	 * @param schema 目标库名,可为空
	 * @param eventType 本处理器负责的事件类型
	 * @return 事件处理器
	 */
	@SuppressWarnings("unchecked")
	private CanalEventHandler createRowEventHandler(CanalRowHandler<?> rowHandler, Class<?> rowType, String table,
			@Nullable String schema, CanalEventType eventType) {
		return new CanalRowEventHandler((CanalRowHandler<Object>) rowHandler, (Class<Object>) rowType, table, schema,
				eventType, this.rowMapper);
	}

	/**
	 * 注册注解驱动的方法:注解里声明的每个事件类型各注册一份.
	 * @param beanName 方法所属 Bean 名称,仅用于报错
	 * @param bean 方法所属 Bean 实例
	 * @param method 标注了 {@link CanalListener} 的方法
	 * @param listener 方法上的注解
	 * @throws IllegalStateException 无法推断表名或参数个数超过两个时抛出
	 */
	private void registerListenerMethod(String beanName, Object bean, Method method, CanalListener listener) {
		Class<?>[] parameterTypes = method.getParameterTypes();
		String table = resolveListenerTable(listener, parameterTypes, beanName, method);
		String schema = resolveListenerSchema(listener, parameterTypes);

		for (CanalEventType eventType : listener.events()) {
			this.dispatcher
				.register(new MethodCanalEventHandler(bean, method, table, schema, eventType, this.rowMapper));
			log.debug("Registered canal listener method '{}#{}' for {} on {}.{}", beanName, method.getName(), eventType,
					schema, table);
		}
	}

	private String resolveListenerTable(CanalListener listener, Class<?>[] parameterTypes, String beanName,
			Method method) {
		if (StringUtils.hasText(listener.table())) {
			return listener.table();
		}
		if (parameterTypes.length > 0 && isRowParameter(parameterTypes[0])) {
			return CanalTableResolver.resolveTable(parameterTypes[0]);
		}

		throw new IllegalStateException("Cannot resolve the target table of canal listener method '" + beanName + "#"
				+ method.getName() + "'; declare CanalListener#table or use a row entity as the first parameter");
	}

	private @Nullable String resolveListenerSchema(CanalListener listener, Class<?>[] parameterTypes) {
		if (StringUtils.hasText(listener.schema())) {
			return listener.schema();
		}
		if (parameterTypes.length > 0 && isRowParameter(parameterTypes[0])) {
			return CanalTableResolver.resolveSchema(parameterTypes[0]);
		}

		return null;
	}

	/**
	 * 判断参数类型是否承载行数据.
	 * <p>
	 * {@link CanalEvent} 与 {@code Map} 是两种"自带表名"之外的参数写法,它们的表名无法推断,必须显式声明
	 * {@link CanalListener#table()}。
	 * @param parameterType 参数类型
	 * @return 是需要映射成实体的行数据参数时返回 {@code true}
	 */
	private static boolean isRowParameter(Class<?> parameterType) {
		return !CanalEvent.class.isAssignableFrom(parameterType) && !Map.class.isAssignableFrom(parameterType);
	}

}
