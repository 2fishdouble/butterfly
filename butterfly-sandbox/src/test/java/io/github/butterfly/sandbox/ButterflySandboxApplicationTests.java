package io.github.butterfly.sandbox;

import io.github.butterfly.GreetingService;
import io.github.butterfly.core.id.IdGenerator;
import io.github.butterfly.core.text.StringUtil;
import io.github.butterfly.core.time.TimeUtil;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端装配测试：仅依赖 butterfly-spring-boot-starter，
 * 验证 core 工具与 GreetingService 都通过自动装配注册成了 Bean。
 */
@SpringBootTest
class ButterflySandboxApplicationTests {

    @Autowired
    private GreetingService greetingService;

    @Autowired
    private StringUtil stringUtil;

    @Autowired
    private IdGenerator idGenerator;

    @Autowired
    private TimeUtil timeUtil;

    @Test
    void contextLoadsAndAllStarterBeansAreWired() {
        assertNotNull(greetingService);
        assertNotNull(stringUtil);
        assertNotNull(idGenerator);
        assertNotNull(timeUtil);
    }

    @Test
    void utilityBeansActuallyWork() {
        // 问候语前缀来自 application.yml 的 butterfly.greeting，只断言格式
        assertTrue(greetingService.greet("World").endsWith(", World!"));
        assertEquals("Hello", stringUtil.capitalize("hello"));
        assertEquals(32, idGenerator.next().length());
        assertEquals(19, timeUtil.now().length());
        assertTrue(stringUtil.isBlank("   "));
    }
}
