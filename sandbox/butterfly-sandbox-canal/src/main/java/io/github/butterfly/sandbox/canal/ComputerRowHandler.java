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

import io.github.butterfly.canal.autoconfigure.CanalRowHandler;
import io.github.butterfly.sandbox.model.Computer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 泛型驱动的处理器示例:泛型参数就是行数据映射的目标类型.
 * <p>
 * 没有标注 {@code @CanalTable} 时表名取实体类简单名首字母小写(这里是 {@code computer}),库名不限;需要限定库名或 换个表名时,重写
 * {@code table()} / {@code schema()},或者改用下面的注解写法。
 * <p>
 * 三个方法都只处理自己关心的事件,这里是全部都打印并记录,供沙箱测试断言。
 */
@Slf4j
@Component
public class ComputerRowHandler implements CanalRowHandler<Computer> {

	/**
	 * 收到的事件描述,供测试断言(消费线程与断言线程不是同一个).
	 */
	public static final BlockingQueue<String> RECEIVED = new LinkedBlockingQueue<>();

	@Override
	public void insert(Computer row) {
		log.info("canal insert: {}", row);
		RECEIVED.add("insert:" + row.getId());
	}

	@Override
	public void update(Computer row, Computer before) {
		log.info("canal update: {} -> {}", before, row);
		RECEIVED.add("update:" + row.getId() + ":" + row.getName());
	}

	@Override
	public void delete(Computer row) {
		log.info("canal delete: {}", row);
		RECEIVED.add("delete:" + row.getId());
	}

}
