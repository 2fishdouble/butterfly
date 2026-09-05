package io.github.butterfly.core;


import cn.hutool.core.util.IdUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
public class ButterflyTests {

    @Test
    public void contextLoads() {
        IdUtil.getSnowflake().nextId();
        log.info("{}", IdUtil.getSnowflake().nextId());
    }
}
