package me.smecsia.smartfox.tools.util;

import org.junit.Test;

import static me.smecsia.smartfox.tools.util.EnumUtil.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Copyright (c) 2012 i-Free. All Rights Reserved.
 *
 * @author Ilya Sadykov
 *         Date: 15.11.12
 *         Time: 17:17
 */
public class EnumUtilTest {

    public static enum TestEnum {
        first,
        second
    }

    @Test
    public void testFromOrdinal() {
        assertEquals(TestEnum.first, fromOrdinal(TestEnum.class, 0));
        assertEquals(TestEnum.second, fromOrdinal(TestEnum.class, 1));
    }

    @Test
    public void testFromString() {
        assertEquals(TestEnum.first, fromString(TestEnum.class, "first"));
    }

    @Test
    public void testRandom() {
        for (int i = 0; i < 100; ++i) {
            assertTrue(enumContains(TestEnum.class, random(TestEnum.class).name()));
        }
    }
}
