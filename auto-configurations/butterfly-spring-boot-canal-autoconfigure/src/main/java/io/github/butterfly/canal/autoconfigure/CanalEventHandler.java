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

/**
 * 事件分发的最小单位:一个"库名 + 表名 + 事件类型"上的一个处理入口.
 * <p>
 * {@link CanalRowHandler} 与 {@link CanalListener} 两种写法最终都会被 {@link CanalHandlerRegistrar}
 * 转换成 {@link CanalEventHandler} 注册到
 * {@link CanalEventDispatcher};需要接入自定义处理器(例如按配置表动态注册)时也可以直接注册本接口的实现。
 */
public interface CanalEventHandler {

	/**
	 * 关心的库名.
	 * @return 库名;返回 {@code null} 表示匹配任意库
	 */
	@Nullable String schema();

	/**
	 * 关心的表名.
	 * @return 表名,必定非空
	 */
	String table();

	/**
	 * 关心的事件类型.
	 * @return 事件类型,必定非空
	 */
	CanalEventType eventType();

	/**
	 * 处理事件.
	 * <p>
	 * 抛出的异常会向上传递给消费端,由它决定回滚与重试,因此处理器不需要自己吞掉异常。
	 * @param event 行变更事件
	 */
	void handle(CanalEvent event);

}
