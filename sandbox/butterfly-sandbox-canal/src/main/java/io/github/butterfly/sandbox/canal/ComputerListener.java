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

package io.github.butterfly.sandbox.canal;

import io.github.butterfly.canal.autoconfigure.CanalEvent;
import io.github.butterfly.canal.autoconfigure.CanalEventType;
import io.github.butterfly.canal.autoconfigure.CanalListener;
import io.github.butterfly.sandbox.model.Computer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 注解驱动的处理器示例:在方法上标注 {@link CanalListener},一个事件类型一个方法.
 * <p>
 * 参数按位置与类型绑定:第一个参数可以是 {@link CanalEvent}(拿完整事件)、{@code Map}(拿列值)或实体类(自动映射); 第二个参数只在
 * UPDATE 事件上注入旧值,这里是映射后的实体。
 */
@Slf4j
@Component
public class ComputerListener {

	/**
	 * 收到的事件描述,供测试断言(消费线程与断言线程不是同一个).
	 */
	public static final BlockingQueue<String> RECEIVED = new LinkedBlockingQueue<>();

	/**
	 * 新增:只需要行数据时直接声明实体类参数.
	 * @param computer 新增后的整行数据
	 */
	@CanalListener(table = "computer", events = CanalEventType.INSERT)
	public void onInsert(Computer computer) {
		log.info("canal listener insert: {}", computer);
		RECEIVED.add("insert:" + computer.getId());
	}

	/**
	 * 更新:需要旧值时声明第二个实体类参数(canal 的旧值默认只包含被修改的列).
	 * @param computer 更新后的行数据
	 * @param before 更新前的旧值
	 */
	@CanalListener(table = "computer", events = CanalEventType.UPDATE)
	public void onUpdate(Computer computer, Computer before) {
		log.info("canal listener update: {} -> {}", before, computer);
		RECEIVED.add("update:" + computer.getId() + ":" + computer.getName());
	}

	/**
	 * 删除:只关心主键时可以直接用 {@link CanalEvent} 取列值,不必映射实体.
	 * @param event 完整事件
	 */
	@CanalListener(table = "computer", events = CanalEventType.DELETE)
	public void onDelete(CanalEvent event) {
		log.info("canal listener delete: {}", event.row());
		RECEIVED.add("delete:" + event.row().get("id"));
	}

}
