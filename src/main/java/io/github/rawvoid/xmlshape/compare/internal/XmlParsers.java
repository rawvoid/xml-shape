package io.github.rawvoid.xmlshape.compare.internal;

import io.github.rawvoid.xmlshape.compare.XmlCompareException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Secure, namespace-aware XML parsing for comparison inputs.
 */
public final class XmlParsers {
    private XmlParsers() {
    }

    public static Document parse(String xml, String label) {
        if (xml == null) {
            throw new XmlCompareException(label + " XML must not be null");
        }
        return parse(new InputSource(new StringReader(xml)), label);
    }

    public static Document parse(Path path, String label) {
        if (path == null) {
            throw new XmlCompareException(label + " path must not be null");
        }
        try (var in = Files.newInputStream(path)) {
            return parse(in, label);
        } catch (IOException e) {
            throw new XmlCompareException("Failed to read " + label + " XML from " + path, e);
        }
    }

    public static Document parse(InputStream inputStream, String label) {
        if (inputStream == null) {
            throw new XmlCompareException(label + " input stream must not be null");
        }
        return parse(new InputSource(inputStream), label);
    }

    public static Document parse(Reader reader, String label) {
        if (reader == null) {
            throw new XmlCompareException(label + " reader must not be null");
        }
        return parse(new InputSource(reader), label);
    }

    public static Element rootElement(Node node, String label) {
        if (node == null) {
            throw new XmlCompareException(label + " node must not be null");
        }
        if (node instanceof Document document) {
            Element root = document.getDocumentElement();
            if (root == null) {
                throw new XmlCompareException(label + " document must have a root element");
            }
            return root;
        }
        if (node instanceof Element element) {
            return element;
        }
        throw new XmlCompareException(label + " node must be a Document or Element, was "
                + node.getClass().getName());
    }

    private static Document parse(InputSource source, String label) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setValidating(false);
            factory.setExpandEntityReferences(false);
            setFeatureQuietly(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
            setFeatureQuietly(factory, "http://xml.org/sax/features/external-general-entities", false);
            setFeatureQuietly(factory, "http://xml.org/sax/features/external-parameter-entities", false);
            setFeatureQuietly(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            var builder = factory.newDocumentBuilder();
            return builder.parse(source);
        } catch (ParserConfigurationException e) {
            throw new XmlCompareException("Failed to configure XML parser for " + label, e);
        } catch (SAXException | IOException e) {
            throw new XmlCompareException("Failed to parse " + label + " XML", e);
        }
    }

    private static void setFeatureQuietly(DocumentBuilderFactory factory, String name, boolean value) {
        try {
            factory.setFeature(name, value);
        } catch (ParserConfigurationException ignored) {
            // Parser implementation may not support the feature; secure defaults applied best-effort.
        }
    }
}
