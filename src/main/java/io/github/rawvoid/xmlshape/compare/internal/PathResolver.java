package io.github.rawvoid.xmlshape.compare.internal;

import io.github.rawvoid.xmlshape.compare.XmlCompareException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves a library path (same dialect as difference paths) to an element under a document root.
 *
 * <p>Examples: {@code /Root[1]/Item[2]}, {@code /{http://ex.com}Root[1]/Child[1]}.
 */
public final class PathResolver {
    private static final Pattern SEGMENT = Pattern.compile(
            "/(?:\\{([^}]*)\\})?([^/\\[\\]@]+)\\[(\\d+)]");

    private PathResolver() {
    }

    public static Element resolve(Element documentRoot, String path, String label) {
        if (documentRoot == null) {
            throw new XmlCompareException(label + " root element must not be null");
        }
        if (path == null || path.isBlank()) {
            return documentRoot;
        }
        String trimmed = path.trim();
        List<Segment> segments = parse(trimmed, label);
        Segment first = segments.getFirst();
        if (!matches(documentRoot, first)) {
            throw new XmlCompareException(label + " root element does not match path first segment "
                    + first + " (was " + display(documentRoot) + ")");
        }
        Element current = documentRoot;
        for (int i = 1; i < segments.size(); i++) {
            current = findChild(current, segments.get(i), label, trimmed);
        }
        return current;
    }

    private static List<Segment> parse(String path, String label) {
        if (!path.startsWith("/")) {
            throw new XmlCompareException(label + " rootPath must start with '/': " + path);
        }
        List<Segment> segments = new ArrayList<>();
        Matcher matcher = SEGMENT.matcher(path);
        int end = 0;
        while (matcher.find()) {
            if (matcher.start() != end) {
                throw new XmlCompareException(label + " invalid rootPath near index " + end + ": " + path);
            }
            String ns = matcher.group(1);
            String local = matcher.group(2);
            int index = Integer.parseInt(matcher.group(3));
            if (index < 1) {
                throw new XmlCompareException(label + " path index must be >= 1: " + path);
            }
            segments.add(new Segment(ns == null ? "" : ns, local, index));
            end = matcher.end();
        }
        if (end != path.length() || segments.isEmpty()) {
            throw new XmlCompareException(label + " invalid rootPath (use /Name[n]/...): " + path);
        }
        return segments;
    }

    private static Element findChild(Element parent, Segment segment, String label, String fullPath) {
        int seen = 0;
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element el = (Element) node;
            if (matches(el, segment)) {
                seen++;
                if (seen == segment.index()) {
                    return el;
                }
            }
        }
        throw new XmlCompareException(label + " element not found for " + segment
                + " under " + display(parent) + " in path " + fullPath);
    }

    private static boolean matches(Element element, Segment segment) {
        return namespaceOf(element).equals(segment.namespaceUri())
                && localNameOf(element).equals(segment.localName());
    }

    private static String display(Element element) {
        String ns = namespaceOf(element);
        String local = localNameOf(element);
        return ns.isEmpty() ? local : "{" + ns + "}" + local;
    }

    private static String namespaceOf(Node node) {
        String ns = node.getNamespaceURI();
        return ns == null ? "" : ns;
    }

    private static String localNameOf(Node node) {
        String local = node.getLocalName();
        if (local != null) {
            return local;
        }
        String name = node.getNodeName();
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
    }

    private record Segment(String namespaceUri, String localName, int index) {
        @Override
        public String toString() {
            if (namespaceUri.isEmpty()) {
                return "/" + localName + "[" + index + "]";
            }
            return "/{" + namespaceUri + "}" + localName + "[" + index + "]";
        }
    }
}
