package io.github.butterfly.core.time;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeUtilTest {

    @Test
    void formatsWithDefaultPattern() {
        TimeUtil timeUtil = new TimeUtil();
        LocalDateTime fixed = LocalDateTime.of(2026, 9, 4, 10, 30, 45);
        assertEquals("2026-09-04 10:30:45", timeUtil.format(fixed));
        assertEquals("yyyy-MM-dd HH:mm:ss", timeUtil.pattern());
    }

    @Test
    void formatsWithCustomPattern() {
        TimeUtil timeUtil = new TimeUtil("yyyy/MM/dd");
        LocalDateTime fixed = LocalDateTime.of(2026, 9, 4, 10, 30, 45);
        assertEquals("2026/09/04", timeUtil.format(fixed));
    }

    @Test
    void nowMatchesDefaultPatternLength() {
        TimeUtil timeUtil = new TimeUtil();
        // yyyy-MM-dd HH:mm:ss => 19 位
        assertEquals(19, timeUtil.now().length());
    }
}
