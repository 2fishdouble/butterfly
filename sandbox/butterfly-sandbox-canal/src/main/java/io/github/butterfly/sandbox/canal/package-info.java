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
 * canal 事件处理器示例包.
 * <p>
 * 两种写法各有一个示例,都会被自动注册并同时收到事件:
 * <ul>
 * <li>泛型驱动:{@link io.github.butterfly.sandbox.canal.ComputerRowHandler},泛型参数即行数据映射的目标类型,
 * 表名由实体类简单名推出;</li>
 * <li>注解驱动:{@link io.github.butterfly.sandbox.canal.ComputerListener},在方法上标注
 * {@code @CanalListener},可按位置与类型绑定事件、行数据与旧值。</li>
 * </ul>
 */
@NullMarked
package io.github.butterfly.sandbox.canal;

import org.jspecify.annotations.NullMarked;
