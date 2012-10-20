package me.smecsia.smartfox.tools.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static me.smecsia.smartfox.tools.util.ExceptionUtil.formatStackTrace;

/**
 *
 * @author Ilya Sadykov
 *         Date: 27.09.12
 *         Time: 18:51
 */
public abstract class BasicService {

    protected Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * Log and throw the exception next
     *
     * @param e exception
     */
    public void logAndThrow(Throwable e){
        logger.error(formatStackTrace(e));
        throw new RuntimeException(e);
    }

    /**
     * Log and throw the exception next
     *
     * @param e exception
     */
    public void logAndThrow(RuntimeException e) {
        logger.error(formatStackTrace(e));
        throw e;
    }

    public void logAndThrow(String message) throws RuntimeException {
        logAndThrow(new RuntimeException(message));
    }
}
