package me.smecsia.smartfox.tools.annotations;

import java.lang.annotation.*;

/**
 * Copyright (c) 2012 i-Free. All Rights Reserved.
 *
 * @author Ilya Sadykov
 *         Date: 19.10.12
 *         Time: 14:28
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SFSSerialize {
    public boolean serialize() default true;

    public boolean deserialize() default true;

    public String name() default "";

    public String[] options() default {};

    public static final class DEFAULT {
        public static SFSSerialize get() {
            return new SFSSerialize() {
                @Override
                public boolean serialize() {
                    return true;
                }

                @Override
                public boolean deserialize() {
                    return true;
                }

                @Override
                public String name() {
                    return "";
                }

                @Override
                public String[] options() {
                    return new String[0];
                }

                @Override
                public Class<? extends Annotation> annotationType() {
                    return SFSSerialize.class;
                }
            };
        }
    }
}
