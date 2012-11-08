package me.smecsia.smartfox.tools.annotations;

import me.smecsia.smartfox.tools.service.BasicAuthService;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 *
 * @author Ilya Sadykov
 *         Date: 13.10.12
 *         Time: 17:07
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Security {

    /**
     * Indicates that this handler requires authentication
     */
    public boolean authRequired() default true;

    /**
     * Defines the custom authentication service
     * @return
     */
    public Class<? extends BasicAuthService> authService() default BasicAuthService.class;

}
