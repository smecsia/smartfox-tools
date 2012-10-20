package me.smecsia.smartfox.tools.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 *
 * @author Ilya Sadykov
 *         Date: 19.10.12
 *         Time: 14:28
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SFSSerialize {
    /**
     * Indicates whatever or not to serialize this field
     * @return
     */
    public boolean serialize() default true;

    /**
     * Indicates whatever or not to deserialize this field
     * @return
     */
    public boolean deserialize() default true;
}
