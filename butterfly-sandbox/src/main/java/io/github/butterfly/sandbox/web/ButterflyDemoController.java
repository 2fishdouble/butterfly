package io.github.butterfly.sandbox.web;

import io.github.butterfly.redis.autoconfigure.Idempotent;
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
    public Map<String, String> index(
            @RequestParam(required = false) Long id
    ) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("service", "butterfly-sandbox");
        log.info("id: {}", id);
        return body;
    }

}
