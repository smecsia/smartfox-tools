package me.smecsia.smartfox.tools.error;

/**
 * @author Ilya Sadykov
 *         Date: 24.09.12
 *         Time: 13:29
 */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String s) {
        super(s);
    }

    public UnauthorizedException(Throwable throwable) {
        super(throwable);
    }
}
