package me.smecsia.smartfox.tools.util;

import org.junit.Test;

import static me.smecsia.smartfox.tools.util.TypesUtil.*;
import static me.smecsia.smartfox.tools.util.TypesUtil.isString;
import static org.junit.Assert.assertTrue;

/**
 * Copyright (c) 2012 i-Free. All Rights Reserved.
 *
 * @author Ilya Sadykov
 *         Date: 19.10.12
 *         Time: 4:06
 */
public class TypesUtilTest {
    @Test
    public void testIsInt() throws Exception {
        assertTrue(isInt(Integer.TYPE));
        assertTrue(isInt(Integer.class));
    }

    @Test
    public void testIsDouble() throws Exception {
        assertTrue(isDouble(Double.TYPE));
        assertTrue(isDouble(Double.class));
    }

    @Test
    public void testIsFloat() throws Exception {
        assertTrue(isFloat(Float.TYPE));
        assertTrue(isFloat(Float.class));
    }

    @Test
    public void testIsBoolean() throws Exception {
        assertTrue(isBoolean(Boolean.TYPE));
        assertTrue(isBoolean(Boolean.class));
    }

    @Test
    public void testIsString() throws Exception {
        assertTrue(isString(String.class));
    }

    @Test
    public void testIsLong() throws Exception {
        assertTrue(isLong(Long.TYPE));
        assertTrue(isLong(Long.class));
    }
}
