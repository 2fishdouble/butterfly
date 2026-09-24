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

package io.github.butterfly.sandbox.web;

import io.github.butterfly.redis.autoconfigure.Idempotent;
import io.github.butterfly.sandbox.model.TimeModuleBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@Slf4j
public class ButterflyDemoController {

	public ButterflyDemoController() {
	}

	@GetMapping("/")
	@Idempotent(lockValue = "#id")
	public Map<String, String> index(@RequestParam(required = false) Long id) {
		Map<String, String> body = new LinkedHashMap<>();
		body.put("service", "butterfly-sandbox");
		log.info("id: {}", id);
		return body;
	}

	@GetMapping("/time")
	public TimeModuleBean time() {
		return new TimeModuleBean();
	}

}
