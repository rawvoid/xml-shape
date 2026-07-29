package io.github.rawvoid.xmlshape.compare;

import java.util.List;

/**
 * Options controlling XML structural comparison.
 *
 * @param ignoreWhitespace   when {@code true}, trim text, collapse internal whitespace, and ignore
 *                           pure-whitespace text nodes (only relevant when {@code compareValues} is
 *                           {@code true})
 * @param orderSensitive     when {@code true}, child nodes must match in document order; when
 *                           {@code false}, element children are aligned by {@code (namespace, localName)}
 *                           (same-name siblings still paired in document order) and significant text
 *                           nodes are compared as a multiset when values are compared
 * @param compareValues      when {@code true}, compare element text and attribute values; when
 *                           {@code false}, only structure is compared (element tree shape, namespaces,
 *                           and attribute name presence — not text or attribute values)
 * @param valueEquality      optional typed/literal equality strategy applied after whitespace handling;
 *                           {@code null} means literal string equality only
 * @param ignoredAttributes  attribute QNames excluded from comparison (presence and value)
 * @param ignoredElements    element QNames excluded entirely (element and descendants omitted)
 * @param rootPath           optional path to the comparison root on both sides (library path dialect);
 *                           {@code null} means the document element
 * @param elementMatches     rules for pairing same-name siblings by business key (takes priority over
 *                           document order for matching element QNames)
 */
public record CompareOptions(
        boolean ignoreWhitespace,
        boolean orderSensitive,
        boolean compareValues,
        ValueEquality valueEquality,
        List<NameRef> ignoredAttributes,
        List<NameRef> ignoredElements,
        String rootPath,
        List<ElementMatch> elementMatches
) {
    public CompareOptions {
        ignoredAttributes = ignoredAttributes == null ? List.of() : List.copyOf(ignoredAttributes);
        ignoredElements = ignoredElements == null ? List.of() : List.copyOf(ignoredElements);
        elementMatches = elementMatches == null ? List.of() : List.copyOf(elementMatches);
        if (rootPath != null && rootPath.isBlank()) {
            rootPath = null;
        }
    }

    public static CompareOptions defaults() {
        return new CompareOptions(true, true, true, null, List.of(), List.of(), null, List.of());
    }

    /**
     * Defaults with {@code compareValues=false}: element tree and attribute names only.
     */
    public static CompareOptions structureOnly() {
        return defaults().withCompareValues(false);
    }

    public CompareOptions withIgnoreWhitespace(boolean ignore) {
        return new CompareOptions(ignore, orderSensitive, compareValues, valueEquality,
                ignoredAttributes, ignoredElements, rootPath, elementMatches);
    }

    public CompareOptions withOrderSensitive(boolean sensitive) {
        return new CompareOptions(ignoreWhitespace, sensitive, compareValues, valueEquality,
                ignoredAttributes, ignoredElements, rootPath, elementMatches);
    }

    public CompareOptions withCompareValues(boolean compareValues) {
        return new CompareOptions(ignoreWhitespace, orderSensitive, compareValues, valueEquality,
                ignoredAttributes, ignoredElements, rootPath, elementMatches);
    }

    public CompareOptions withValueEquality(ValueEquality valueEquality) {
        return new CompareOptions(ignoreWhitespace, orderSensitive, compareValues, valueEquality,
                ignoredAttributes, ignoredElements, rootPath, elementMatches);
    }

    /**
     * Replaces the ignored-attribute list (does not append).
     */
    public CompareOptions withIgnoreAttributes(NameRef... attributes) {
        return new CompareOptions(ignoreWhitespace, orderSensitive, compareValues, valueEquality,
                attributes == null ? List.of() : List.of(attributes), ignoredElements, rootPath,
                elementMatches);
    }

    /**
     * Replaces the ignored-element list (does not append).
     */
    public CompareOptions withIgnoreElements(NameRef... elements) {
        return new CompareOptions(ignoreWhitespace, orderSensitive, compareValues, valueEquality,
                ignoredAttributes, elements == null ? List.of() : List.of(elements), rootPath,
                elementMatches);
    }

    /**
     * Sets the comparison root path on both documents, e.g. {@code /Root[1]/Body[1]}.
     * Pass {@code null} to compare from the document element.
     */
    public CompareOptions withRootPath(String rootPath) {
        return new CompareOptions(ignoreWhitespace, orderSensitive, compareValues, valueEquality,
                ignoredAttributes, ignoredElements, rootPath, elementMatches);
    }

    /**
     * Replaces element key-matching rules (does not append). Matching QNames are paired by key
     * regardless of {@link #orderSensitive()}.
     */
    public CompareOptions withElementMatches(ElementMatch... matches) {
        return new CompareOptions(ignoreWhitespace, orderSensitive, compareValues, valueEquality,
                ignoredAttributes, ignoredElements, rootPath,
                matches == null ? List.of() : List.of(matches));
    }
}
