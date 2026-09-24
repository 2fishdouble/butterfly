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

import java.util.List;

/**
 * 同步消费:在拉取线程里按事件顺序逐个分发,一个处理完再处理下一个,整批成功后才确认.
 * <p>
 * 顺序有保证、排查问题最直观,代价是吞吐受处理器耗时限制:一批里任何一个事件慢,整批确认都会被拖慢。适用于处理逻辑 轻量、对顺序敏感(例如同一行的增删改必须按序生效)的场景。
 * <p>
 * 由 {@code butterfly.canal.consumer-type=sync}(默认值)启用。
 */
public class SyncCanalEventConsumer extends AbstractCanalEventConsumer {

	/**
	 * 创建同步消费端.
	 * @param messageSource 消息来源
	 * @param dispatcher 事件分发器
	 * @param properties canal 配置
	 */
	public SyncCanalEventConsumer(CanalMessageSource messageSource, CanalEventDispatcher dispatcher,
			CanalProperties properties) {
		super(messageSource, dispatcher, properties);
	}

	/**
	 * 顺序分发本批事件;任一处理器抛出异常都会中断本批,让整批回滚重投.
	 * @param events 本批事件
	 */
	@Override
	protected void process(List<CanalEvent> events) {
		for (CanalEvent event : events) {
			dispatcher().dispatch(event);
		}
	}

	@Override
	protected String consumerType() {
		return "sync";
	}

}
