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
 * Butterfly 核心通用类型:统一响应包装 {@link io.github.butterfly.core.R}、业务异常
 * {@link io.github.butterfly.core.BusinessException}、枚举统一接口
 * {@link io.github.butterfly.core.BaseEnum} 与日期时间格式常量集合
 * {@link io.github.butterfly.core.PatternConstant}, 供各业务模块与自动配置模块共用.
 * <p>
 * 本包整体标记为 {@link org.jspecify.annotations.NullMarked}, 即包内 API 默认按非空约定处理; 确需允许空值处需显式标注
 * {@link org.jspecify.annotations.Nullable}。
 */
@NullMarked
package io.github.butterfly.core;

import org.jspecify.annotations.NullMarked;
