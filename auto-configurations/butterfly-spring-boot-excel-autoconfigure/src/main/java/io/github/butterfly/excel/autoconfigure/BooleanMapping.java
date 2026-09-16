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

package io.github.butterfly.excel.autoconfigure;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 布尔列与 Excel 单元格文本的映射注解,标注在实体布尔字段上.
 * <p>
 * {@link BooleanStringGenericConverter} 读出该注解后:导出时用 {@link #trueValue()} /
 * {@link #falseValue()} 生成单元格文本,导入时按同样两个文本反解为 {@code true} / {@code false};
 * 若单元格文本与两者都不相等,则退回 {@code Boolean.valueOf(String)} 的默认规则。
 */
@Target({ ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface BooleanMapping {

	/**
	 * 布尔值 {@code true} 在 Excel 中对应的文本,导出与导入双向使用.
	 * @return {@code true} 的映射文本
	 */
	String trueValue();

	/**
	 * 布尔值 {@code false} 在 Excel 中对应的文本,导出与导入双向使用.
	 * @return {@code false} 的映射文本
	 */
	String falseValue();

	/**
	 * 布尔值为 {@code null} 时的占位文本,默认空串.当前 {@link BooleanStringGenericConverter} 未读取该属性.
	 * @return {@code null} 的占位文本
	 */
	String nullValue() default "";

}
