package io.github.rawvoid.xmlshape.compare;

/**
 * Kind of structural mismatch found while comparing two XML documents.
 */
public enum DifferenceType {
    /** Element local names differ. */
    ELEMENT_NAME,
    /** Element namespace URIs differ. */
    ELEMENT_NAMESPACE,
    /** An expected element has no counterpart on the actual side. */
    ELEMENT_MISSING,
    /** An actual element has no counterpart on the expected side. */
    ELEMENT_UNEXPECTED,
    /** An expected attribute is absent on the actual element. */
    ATTRIBUTE_MISSING,
    /** An actual attribute is not present on the expected element. */
    ATTRIBUTE_UNEXPECTED,
    /** Attribute values differ for the same attribute QName. */
    ATTRIBUTE_VALUE,
    /** Character data values differ. */
    TEXT_VALUE,
    /** Child node kinds differ (e.g. element vs text) at the same position. */
    NODE_TYPE
}
