package me.smecsia.smartfox.tools.error;

/**
 * @author Ilya Sadykov
 *         Date: 27.09.12
 *         Time: 18:55
 */
public class MetadataException extends RuntimeException {
    public MetadataException(Throwable t) {
        super(t);
    }

    public MetadataException(String message) {
        super(message);
    }
}
