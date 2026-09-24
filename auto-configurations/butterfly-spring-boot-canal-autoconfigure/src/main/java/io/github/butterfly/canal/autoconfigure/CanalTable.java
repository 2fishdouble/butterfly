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
 * 声明实体类对应的库表,供 {@link CanalRowHandler} 与 {@link CanalListener} 解析表名与库名.
 * <p>
 * 不标注时表名取实体类简单名首字母小写({@code Computer} → {@code computer}),库名不限(匹配任意库)。
 * {@code @CanalTable} 可以放在实体类上,也可以通过元注解组合进自定义注解({@link CanalTableResolver} 解析的是合并后的注解属性)。
 * <p>
 * 实体类来自第三方、无法标注时,改为重写 {@link CanalRowHandler#table()} 与
 * {@link CanalRowHandler#schema()},或在 {@link CanalListener} 上显式指定。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CanalTable {

	/**
	 * 表名.
	 * @return 表名;为空串时取实体类简单名首字母小写
	 */
	String value() default "";

	/**
	 * 库名,即 MySQL 的 database 名.
	 * @return 库名;为空串时不限库名,匹配任意库
	 */
	String schema() default "";

}
