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
 * 示例数据模型包.
 * <p>
 * 存放沙箱示例使用的数据结构:商品信息 {@link io.github.butterfly.sandbox.model.Computer}(含嵌套的商品明细)、 时间信息
 * {@link io.github.butterfly.sandbox.model.TimeModuleBean},以及 canal 示例的显卡信息
 * {@link io.github.butterfly.sandbox.model.Gpu};枚举
 * {@link io.github.butterfly.sandbox.model.WeekType} 是编码枚举 (实现
 * {@link io.github.butterfly.core.BaseEnum}),库中按 code 存放.
 */
@NullMarked
package io.github.butterfly.sandbox.model;

import org.jspecify.annotations.NullMarked;
