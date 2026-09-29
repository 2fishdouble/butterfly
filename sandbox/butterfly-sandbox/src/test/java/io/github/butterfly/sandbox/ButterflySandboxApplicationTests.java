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

package io.github.butterfly.sandbox;

import io.github.butterfly.mail.autoconfigure.MailTemplate;
import io.github.butterfly.sandbox.model.Computer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 上下文启动校验:自动配置提供的各模板 Bean 都能被正常注入.
 */
@SpringBootTest
class ButterflySandboxApplicationTests {

	@Autowired
	private RedisTemplate<String, String> stringRedisTemplate;

	@Autowired
	private RedisTemplate<Object, Object> objectRedisTemplate;

	@Autowired
	private RedisTemplate<String, Computer> computerRedisTemplate;

	@Autowired
	private MailTemplate mailTemplate;

	/**
	 * 各模板 Bean 均已注册且注入成功.
	 */
	@Test
	void templateBeansAreInjected() {
		assertThat(this.stringRedisTemplate).isNotNull();
		assertThat(this.objectRedisTemplate).isNotNull();
		assertThat(this.computerRedisTemplate).isNotNull();
		assertThat(this.mailTemplate).isNotNull();
	}

}
