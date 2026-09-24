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
 * canal 沙箱启动包.
 * <p>
 * 启动类所在包,演示直连 canal server 消费 MySQL binlog,并把 INSERT、UPDATE、DELETE 三类行变更事件路由到处理器;具体处理器见
 * {@link io.github.butterfly.sandbox.canal}.
 */
@NullMarked
package io.github.butterfly.sandbox;

import org.jspecify.annotations.NullMarked;
