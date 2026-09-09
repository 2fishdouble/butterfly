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
        computer.setProducts(List.of(
                new Computer.Product() {{
                    setId(614999623461548033L);
                    setName("product");
                    setPrice(new BigDecimal("1.23695"));
                    setQuantity(1);
                    setTotalAmount(BigDecimal.ONE);
                    setDescription("description");
                }}
        ));
        computerRedisTemplate.opsForValue().set("computer:1", computer);
    }

    @Test
    void mailTest() {
        mailTemplate.sendAttachment("cjd0655@gmail.com", "测试邮件", "这是一封测试邮件",
                Map.of("README.md", new ClassPathResource("README.md")));
    }
}
