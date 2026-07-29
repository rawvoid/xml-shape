package io.github.rawvoid.xmlshape.compare;

/**
 * Context for a value being compared (element text or attribute).
 *
 * @param kind         source of the value
 * @param namespaceUri  namespace of the element (for text) or attribute; empty string if none
 * @param localName    local name of the parent element (for text) or of the attribute
 * @param path         comparison path (element path, or element path plus {@code /@attr})
 */
public record ValueContext(
        ValueKind kind,
        String namespaceUri,
        String localName,
        String path
) {
    public ValueContext {
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
        if (namespaceUri == null) {
            namespaceUri = "";
        }
        if (localName == null) {
            throw new IllegalArgumentException("localName must not be null");
        }
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
    }
}
