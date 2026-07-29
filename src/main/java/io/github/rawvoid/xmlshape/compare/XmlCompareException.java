package io.github.rawvoid.xmlshape.compare;

/**
 * Thrown when XML comparison cannot proceed (for example invalid XML input).
 */
public class XmlCompareException extends RuntimeException {
    public XmlCompareException(String message) {
        super(message);
    }

    public XmlCompareException(String message, Throwable cause) {
        super(message, cause);
    }
}
