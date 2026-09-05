package io.github.butterfly.sandbox.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;


@RestController
public class ButterflyDemoController {


    public ButterflyDemoController() {
    }

    @GetMapping("/")
    public Map<String, String> index() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("service", "butterfly-sandbox");
        return body;
    }

}
