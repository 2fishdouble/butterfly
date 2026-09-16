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
 * Butterfly 基础自动配置包.
 * <p>
 * 提供框架通用的 Jackson 定制({@code BaseEnum} 枚举按 code 收发、{@code Long} 转字符串、 日期时间格式统一)、SpEL
 * 表达式解析辅助({@code SpelSup})以及 Servlet Web 环境下的全局异常兜底处理; 各 Bean 均按可选依赖与现有 Bean
 * 条件注册,缺失依赖时静默跳过。
 * <p>
 * 包内类型默认非空(见 {@code @NullMarked}),需要允许空值的成员须显式标注可空。
 */
@NullMarked
package io.github.butterfly.autoconfigure;

import org.jspecify.annotations.NullMarked;
