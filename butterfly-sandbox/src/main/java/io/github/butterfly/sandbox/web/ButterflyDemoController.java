package io.github.butterfly.sandbox.web;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.butterfly.GreetingService;
import io.github.butterfly.core.id.IdGenerator;
import io.github.butterfly.core.text.StringUtil;
import io.github.butterfly.core.time.TimeUtil;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 演示 starter 自动装配的 Bean：core 工具（StringUtil / IdGenerator / TimeUtil）
 * 与 GreetingService 都能被直接注入使用。
 */
@RestController
public class ButterflyDemoController {

    private final GreetingService greetingService;
    private final StringUtil stringUtil;
    private final IdGenerator idGenerator;
    private final TimeUtil timeUtil;

    public ButterflyDemoController(GreetingService greetingService, StringUtil stringUtil,
            IdGenerator idGenerator, TimeUtil timeUtil) {
        this.greetingService = greetingService;
        this.stringUtil = stringUtil;
        this.idGenerator = idGenerator;
        this.timeUtil = timeUtil;
    }

    @GetMapping("/")
    public Map<String, String> index() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("service", "butterfly-sandbox");
        body.put("greeting", greetingService.greet("Butterfly"));
        body.put("now", timeUtil.now());
        return body;
    }

    @GetMapping("/hello")
    public String hello(@RequestParam(defaultValue = "World") String name) {
        String cleaned = stringUtil.capitalize(name.trim());
        return greetingService.greet(cleaned);
    }

    @GetMapping("/id")
    public Map<String, String> id() {
        return Map.of("id", idGenerator.next());
    }

    @GetMapping("/util")
    public Map<String, Object> util(@RequestParam(defaultValue = "  butterfly demo  ") String text) {
        String trimmed = text.trim();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("raw", text);
        body.put("isBlank", stringUtil.isBlank(text));
        body.put("capitalized", stringUtil.capitalize(trimmed));
        body.put("truncated", stringUtil.truncate(trimmed, 6));
        return body;
    }
}
