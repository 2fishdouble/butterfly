package io.github.butterfly.sandbox;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Butterfly Starter 演示应用入口。
 *
 * <p>运行前需先对 butterfly 工程执行 {@code ./mvnw install}，然后
 * {@code ./mvnw -f butterfly-sandbox/pom.xml spring-boot:run}。
 */
@SpringBootApplication
public class ButterflySandboxApplication {

    public static void main(String[] args) {
        SpringApplication.run(ButterflySandboxApplication.class, args);
    }
}
