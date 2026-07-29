package io.github.rawvoid.xmlshape.compare;

import java.util.Objects;

/**
 * Reference to an element or attribute by namespace URI and local name.
 *
 * @param namespaceUri target namespace, or empty string when unqualified
 * @param localName   local name (required, non-empty)
 */
public record NameRef(String namespaceUri, String localName) {
    public NameRef {
        namespaceUri = namespaceUri == null ? "" : namespaceUri;
        Objects.requireNonNull(localName, "localName");
        if (localName.isEmpty()) {
            throw new IllegalArgumentException("localName must not be empty");
        }
    }

    public static NameRef local(String localName) {
        return new NameRef("", localName);
    }

    public static NameRef of(String namespaceUri, String localName) {
        return new NameRef(namespaceUri, localName);
    }

    public boolean matches(String namespaceUri, String localName) {
        String ns = namespaceUri == null ? "" : namespaceUri;
        return this.namespaceUri.equals(ns) && this.localName.equals(localName);
    }

    @Override
    public String toString() {
        if (namespaceUri.isEmpty()) {
            return localName;
        }
        return "{" + namespaceUri + "}" + localName;
    }
}
