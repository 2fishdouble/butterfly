package io.github.butterfly;

/**
 * 功能类示例：使用配置项 butterfly.greeting 拼接问候语。
 */
public class GreetingService {

    private final String greeting;

    public GreetingService(String greeting) {
        this.greeting = greeting;
    }

    public String greet(String name) {
        return greeting + ", " + name + "!";
    }
}
