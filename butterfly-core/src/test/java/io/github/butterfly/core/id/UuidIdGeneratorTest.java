package io.github.butterfly.core.id;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UuidIdGeneratorTest {

    private static final java.util.regex.Pattern HEX32 =
            java.util.regex.Pattern.compile("^[0-9a-f]{32}$");

    private final IdGenerator idGenerator = new UuidIdGenerator();

    @Test
    void generates32CharsLowercaseHexWithoutDash() {
        String id = idGenerator.next();
        assertEquals(32, id.length());
        assertTrue(HEX32.matcher(id).matches(), "should be 32 lowercase hex chars: " + id);
    }

    @Test
    void idsAreUniqueInBatch() {
        int count = 10_000;
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < count; i++) {
            ids.add(idGenerator.next());
        }
        assertEquals(count, ids.size(), "all generated ids should be unique");
    }
}
