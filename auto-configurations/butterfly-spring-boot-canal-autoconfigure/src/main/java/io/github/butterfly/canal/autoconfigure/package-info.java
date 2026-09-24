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

/**
 * canal 支持:基于 canal-client 直连 canal server 订阅 binlog 增量,把 INSERT、UPDATE、DELETE 三类行变更事件按
 * 库名、表名与事件类型路由到使用方的处理器.
 * <p>
 * 消费方式由 {@link io.github.butterfly.canal.autoconfigure.CanalConsumerType}
 * 选择:同步消费(处理器在拉取线程内 串行执行)或异步消费(事件提交到线程池并发执行,整批处理完才确认)。
 * <p>
 * 处理器有两种写法,都由 {@link io.github.butterfly.canal.autoconfigure.CanalHandlerRegistrar}
 * 自动发现:实现 {@link io.github.butterfly.canal.autoconfigure.CanalRowHandler}
 * 的泛型驱动写法,泛型参数即行数据映射的实体类; 以及在方法上标注
 * {@link io.github.butterfly.canal.autoconfigure.CanalListener} 的注解驱动写法。两种写法都按
 * 库名、表名与事件类型匹配,行数据到实体的转换由 {@link io.github.butterfly.canal.autoconfigure.CanalRowMapper}
 * 负责,可自行替换。
 */
@NullMarked
package io.github.butterfly.canal.autoconfigure;

import org.jspecify.annotations.NullMarked;
