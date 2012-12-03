package me.smecsia.smartfox.tools.util;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Random;

/**
 * Copyright (c) 2012 i-Free. All Rights Reserved.
 *
 * @author Ilya Sadykov
 *         Date: 22.11.12
 *         Time: 18:57
 */
public class RandomUtil {

    private static final SecureRandom random = new SecureRandom();
    private static Random randomValue = new Random();

    public static int randomInt(int max) {
        return randomValue.nextInt(max);
    }

    public static int randomInt() {
        return randomValue.nextInt();
    }

    public static String randomString() {
        return new BigInteger(130, random).toString(32);
    }
}
