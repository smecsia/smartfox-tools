package me.smecsia.smartfox.tools.error;

import com.smartfoxserver.v2.exceptions.SFSRuntimeException;

/**
 *
 * @author Ilya Sadykov
 *         Date: 24.09.12
 *         Time: 13:29
 */
public class UnauthorizedException extends SFSRuntimeException {
    public UnauthorizedException(String s) {
        super(s);
    }

    public UnauthorizedException(Throwable throwable) {
        super(throwable);
    }
}
