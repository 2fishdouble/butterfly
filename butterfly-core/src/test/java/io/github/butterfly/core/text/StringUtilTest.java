package io.github.butterfly.core.text;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StringUtilTest {

    private StringUtil stringUtil;

    @BeforeEach
    void setUp() {
        stringUtil = new StringUtil();
    }

    @Test
    void isBlankAcceptsNullEmptyAndWhitespace() {
        assertTrue(stringUtil.isBlank(null));
        assertTrue(stringUtil.isBlank(""));
        assertTrue(stringUtil.isBlank("   \t\n"));
    }

    @Test
    void isBlankRejectsNonBlank() {
        assertFalse(stringUtil.isBlank("  a  "));
        assertFalse(stringUtil.isBlank("text"));
    }

    @Test
    void isNotBlankIsOppositeOfIsBlank() {
        assertTrue(stringUtil.isNotBlank("text"));
        assertFalse(stringUtil.isNotBlank("  "));
        assertFalse(stringUtil.isNotBlank(null));
    }

    @Test
    void truncateReturnsNullForNullInput() {
        assertNull(stringUtil.truncate(null, 5));
    }

    @Test
    void truncateKeepsShortTextAsIs() {
        String text = "abc";
        assertEquals(text, stringUtil.truncate(text, 5));
        assertEquals(text, stringUtil.truncate(text, 3));
    }

    @Test
    void truncateCutsLongTextToMaxLength() {
        assertEquals("abc", stringUtil.truncate("abcdef", 3));
        assertEquals("", stringUtil.truncate("abcdef", 0));
    }

    @Test
    void capitalizeFirstCharacter() {
        assertEquals("Hello", stringUtil.capitalize("hello"));
        assertEquals("World", stringUtil.capitalize("World"));
        assertEquals("ABC", stringUtil.capitalize("aBC"));
    }

    @Test
    void capitalizeHandlesBlank() {
        assertEquals("", stringUtil.capitalize(""));
        assertNull(stringUtil.capitalize(null));
        assertEquals("   ", stringUtil.capitalize("   "));
    }
}
