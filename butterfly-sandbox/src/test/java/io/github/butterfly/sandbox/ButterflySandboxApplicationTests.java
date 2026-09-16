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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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

	@BeforeEach
	void setUp() {
	}

	@AfterEach
	void tearDown() {
	}

	@Test
	void contextLoads() {
		Computer computer = new Computer();
		computer.setId(614999623461548032L);
		computer.setCreateTime(LocalDateTime.now());
		computer.setProducts(List.of(new Computer.Product() {
			{
				setId(614999623461548033L);
				setName("product");
				setPrice(new BigDecimal("1.23695"));
				setQuantity(1);
				setTotalAmount(BigDecimal.ONE);
				setDescription("description");
			}
		}));
		this.computerRedisTemplate.opsForValue().set("computer:1", computer);
	}

	@Test
	void mailTest() {
		this.mailTemplate.sendAttachment("cjd0655@gmail.com", "测试邮件", "这是一封测试邮件",
				Map.of("README.md", new ClassPathResource("README.md")));
	}

}
