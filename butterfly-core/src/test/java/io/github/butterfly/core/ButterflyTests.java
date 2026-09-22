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

package io.github.butterfly.core;

import cn.hutool.core.util.IdUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Locale;

@Slf4j
public class ButterflyTests {

	/**
	 * VM options: -Duser.language=en -Duser.country=CA.
	 */
	@Test
	public void contextLoads() {
		IdUtil.getSnowflake().nextId();
		log.info("{}", IdUtil.getSnowflake().nextId());
		log.info("Default locale: {}", Locale.getDefault());
		log.info("Language: {}", Locale.getDefault().getLanguage());
		log.info("Country: {}", Locale.getDefault().getCountry());
	}

}
