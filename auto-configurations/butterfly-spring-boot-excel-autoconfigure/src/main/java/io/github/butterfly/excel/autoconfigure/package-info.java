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
 * Butterfly 的 Excel 读写支撑包,基于 FastExcel 提供可直接调用的工具与转换器.
 * <p>
 * 本包不提供任何自动配置类:使用方按需直接调用 {@link io.github.butterfly.excel.autoconfigure.ExcelUtil}
 * 完成读、写与下载包装,并在需要时注册 {@link io.github.butterfly.excel.autoconfigure.BaseEnumConverter}、
 * {@link io.github.butterfly.excel.autoconfigure.CollectionBaseEnumConverter}、
 * {@link io.github.butterfly.excel.autoconfigure.BooleanStringGenericConverter} 等转换器。
 */
@NullMarked
package io.github.butterfly.excel.autoconfigure;

import org.jspecify.annotations.NullMarked;
