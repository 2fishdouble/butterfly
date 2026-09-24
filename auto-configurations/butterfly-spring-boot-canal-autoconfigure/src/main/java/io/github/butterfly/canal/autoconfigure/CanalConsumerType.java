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

/**
 * 事件处理方式,前缀 {@code butterfly.canal.consumer-type}.
 * <p>
 * 两种方式都遵守"先处理、后确认"的顺序:只有整批事件都处理成功才向 canal 确认(ack),任一事件抛出异常
 * 就整批回滚(rollback),因此都是至少一次(at-least-once)语义,处理器需要自己保证幂等。
 */
public enum CanalConsumerType {

	/**
	 * 同步消费:在拉取线程内按顺序逐个调用处理器,一个事件处理完再处理下一个.
	 * <p>
	 * 顺序有保证、实现最简单,但吞吐受处理器耗时限制。数据一致性要求高、处理逻辑轻量的场景用这个。
	 */
	SYNC,

	/**
	 * 异步消费:整批事件提交到线程池并发处理,全部完成后再确认.
	 * <p>
	 * 同一批内的事件不再有序,吞吐随线程数提升;确认仍然是整批的,因此某个事件失败会让整批(包括已成功的事件) 重新投递。线程池参数见
	 * {@link CanalProperties.Async}。
	 */
	ASYNC

}
