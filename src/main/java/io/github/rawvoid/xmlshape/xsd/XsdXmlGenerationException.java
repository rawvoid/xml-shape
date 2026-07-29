package io.github.rawvoid.xmlshape.xsd;

/**
 * Thrown when schema loading or XML instance generation fails.
 */
public class XsdXmlGenerationException extends RuntimeException {
    public XsdXmlGenerationException(String message) {
        super(message);
    }

    public XsdXmlGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
