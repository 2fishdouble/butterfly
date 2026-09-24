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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 注解驱动的行变更处理器:标注在 Bean 的方法上,按库名、表名与事件类型接收行变更事件.
 * <p>
 * 例如只处理新增与删除: <pre>{@code
 * &#64;Component
 * class ComputerListener {
 *

 *     &#64;CanalListener(table = "computer", events = CanalEventType.INSERT)
 *     public void onInsert(Computer computer) {
 *         // 处理新增,computer 已由 CanalRowMapper 映射好
 *     }
 *
 *
&#64;CanalListener(table = "computer", events = { CanalEventType.UPDATE, CanalEventType.DELETE })
 *     public void onUpdateAndDelete(CanalEvent event) {
 *         // 需要库名、SQL、执行时间等元数据时直接声明 CanalEvent 参数
 *     }
 * }
 * }</pre>
 * <p>
 * <b>表名</b>取 {@link #table()};未指定时取方法第一个参数类型的 {@link CanalTable} 声明,或在参数类型简单名首字母
 * 小写。方法没有参数却又不指定 {@link #table()} 时启动直接报错,因为这时的目标表无从推断。
 * <p>
 * <b>库名</b>取 {@link #schema()};未指定时取第一个参数类型的 {@link CanalTable#schema()},都取不到时不限库名。
 * <p>
 * <b>方法参数</b>最多两个,按位置与类型绑定:
 * <ol>
 * <li>第一个参数:声明 {@link CanalEvent} 时注入完整事件;声明 {@code Map} 时注入列名到列值的映射;声明其它类型时 用
 * {@link CanalRowMapper} 把目标行映射成该类型。</li>
 * <li>第二个参数(可选):接收 UPDATE 事件的旧值,类型规则与第一个参数相同;非 UPDATE 事件注入 {@code null}。</li>
 * </ol>
 * 参数超过两个时启动直接报错。
 * <p>
 * 同一个方法可以声明多个 {@link #events()},此时每个事件类型各注册一份处理器。方法抛出的异常由消费端处理:同步消费
 * 会中断本批处理并回滚,异步消费会让整批重新投递。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CanalListener {

	/**
	 * 表名.
	 * @return 表名;为空串时取方法第一个参数类型的 {@link CanalTable} 声明或参数类型简单名首字母小写
	 */
	String table() default "";

	/**
	 * 库名,即 MySQL 的 database 名.
	 * @return 库名;为空串时取第一个参数类型的 {@link CanalTable#schema()},都取不到时不限库名
	 */
	String schema() default "";

	/**
	 * 本方法关心的事件类型.
	 * @return 事件类型数组,默认 INSERT、UPDATE、DELETE 全部关心
	 */
	CanalEventType[] events() default { CanalEventType.INSERT, CanalEventType.UPDATE, CanalEventType.DELETE };

}
