package io.github.butterfly.sandbox;

import io.github.butterfly.redis.autoconfigure.EnableRedisTemplates;
import io.github.butterfly.sandbox.model.Computer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication
@EnableRedisTemplates(value = Computer.class)
public class ButterflySandboxApplication {

    public static void main(String[] args) {
        SpringApplication.run(ButterflySandboxApplication.class, args);
    }
}
