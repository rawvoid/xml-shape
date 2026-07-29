package io.github.rawvoid.xmlshape.compare;

import io.github.rawvoid.xmlshape.compare.internal.PathResolver;
import io.github.rawvoid.xmlshape.compare.internal.StructuralComparator;
import io.github.rawvoid.xmlshape.compare.internal.XmlParsers;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Path;

/**
 * Compares two XML documents by structure: element QNames, attributes, and optionally text / attribute
 * values ({@link CompareOptions#compareValues()}).
 *
 * <p>Namespace prefixes are ignored; identity uses {@code (namespace URI, local name)}.
 * Namespace declaration attributes ({@code xmlns}/{@code xmlns:*}) are not compared.
 * Comments and processing instructions are ignored.
 *
 * <p>When {@link CompareOptions#orderSensitive()} is {@code false}, element children are aligned by
 * QName (same-name siblings paired in document order) and significant direct text nodes are compared
 * as a multiset when values are enabled — not full subtree isomorphism matching.
 *
 * <p>When {@code compareValues} is {@code false}, only the element tree and attribute name presence
 * are checked; text content and attribute values are ignored.
 *
 * <p>Inputs may be {@link String}, {@link Path}, {@link Node} ({@code Document} or {@code Element}),
 * {@link InputStream}, or {@link Reader} (symmetric overloads only).
 */
public final class XmlComparer {
    private XmlComparer() {
    }

    public static XmlDiff compare(String expectedXml, String actualXml) {
        return compare(expectedXml, actualXml, CompareOptions.defaults());
    }

    public static XmlDiff compare(String expectedXml, String actualXml, CompareOptions options) {
        Element expected = XmlParsers.rootElement(XmlParsers.parse(expectedXml, "expected"), "expected");
        Element actual = XmlParsers.rootElement(XmlParsers.parse(actualXml, "actual"), "actual");
        return compareElements(expected, actual, options);
    }

    public static XmlDiff compare(Path expectedPath, Path actualPath) {
        return compare(expectedPath, actualPath, CompareOptions.defaults());
    }

    public static XmlDiff compare(Path expectedPath, Path actualPath, CompareOptions options) {
        Element expected = XmlParsers.rootElement(XmlParsers.parse(expectedPath, "expected"), "expected");
        Element actual = XmlParsers.rootElement(XmlParsers.parse(actualPath, "actual"), "actual");
        return compareElements(expected, actual, options);
    }

    public static XmlDiff compare(Node expectedNode, Node actualNode) {
        return compare(expectedNode, actualNode, CompareOptions.defaults());
    }

    public static XmlDiff compare(Node expectedNode, Node actualNode, CompareOptions options) {
        Element expected = XmlParsers.rootElement(expectedNode, "expected");
        Element actual = XmlParsers.rootElement(actualNode, "actual");
        return compareElements(expected, actual, options);
    }

    public static XmlDiff compare(InputStream expectedXml, InputStream actualXml) {
        return compare(expectedXml, actualXml, CompareOptions.defaults());
    }

    public static XmlDiff compare(InputStream expectedXml, InputStream actualXml, CompareOptions options) {
        Element expected = XmlParsers.rootElement(XmlParsers.parse(expectedXml, "expected"), "expected");
        Element actual = XmlParsers.rootElement(XmlParsers.parse(actualXml, "actual"), "actual");
        return compareElements(expected, actual, options);
    }

    public static XmlDiff compare(Reader expectedXml, Reader actualXml) {
        return compare(expectedXml, actualXml, CompareOptions.defaults());
    }

    public static XmlDiff compare(Reader expectedXml, Reader actualXml, CompareOptions options) {
        Element expected = XmlParsers.rootElement(XmlParsers.parse(expectedXml, "expected"), "expected");
        Element actual = XmlParsers.rootElement(XmlParsers.parse(actualXml, "actual"), "actual");
        return compareElements(expected, actual, options);
    }

    private static XmlDiff compareElements(Element expected, Element actual, CompareOptions options) {
        if (options == null) {
            options = CompareOptions.defaults();
        }
        if (options.rootPath() != null) {
            expected = PathResolver.resolve(expected, options.rootPath(), "expected");
            actual = PathResolver.resolve(actual, options.rootPath(), "actual");
        }
        var differences = new StructuralComparator(options).compare(expected, actual);
        return new XmlDiff(differences);
    }
}

