package me.smecsia.smartfox.tools.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Copyright (c) 2012 i-Free. All Rights Reserved.
 *
 * @author Ilya Sadykov
 *         Date: 19.10.12
 *         Time: 14:28
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface SFSSerializeStrategy {
    public static enum Strategy {
        ANNOTATED_FIELDS,
        ALL_FIELDS
    }
    public Strategy type() default Strategy.ALL_FIELDS;
}
