package io.github.rawvoid.xmlshape.compare;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Objects;
import java.util.Optional;

/**
 * Rule for pairing same-name sibling elements by a business key instead of document order.
 */
public final class ElementMatch {
    private final NameRef element;
    private final NameRef attribute;
    private final NameRef child;

    private ElementMatch(NameRef element, NameRef attribute, NameRef child) {
        this.element = Objects.requireNonNull(element, "element");
        this.attribute = attribute;
        this.child = child;
        if ((attribute == null) == (child == null)) {
            throw new IllegalArgumentException("exactly one of attribute or child key must be set");
        }
    }

    public static ElementMatch byAttribute(NameRef element, NameRef attribute) {
        return new ElementMatch(element, Objects.requireNonNull(attribute, "attribute"), null);
    }

    public static ElementMatch byAttribute(String elementLocal, String attributeLocal) {
        return byAttribute(NameRef.local(elementLocal), NameRef.local(attributeLocal));
    }

    public static ElementMatch byAttribute(
            String elementNs, String elementLocal, String attributeNs, String attributeLocal) {
        return byAttribute(NameRef.of(elementNs, elementLocal), NameRef.of(attributeNs, attributeLocal));
    }

    public static ElementMatch byChildText(NameRef element, NameRef childElement) {
        return new ElementMatch(element, null, Objects.requireNonNull(childElement, "childElement"));
    }

    public static ElementMatch byChildText(String elementLocal, String childLocal) {
        return byChildText(NameRef.local(elementLocal), NameRef.local(childLocal));
    }

    public static ElementMatch byChildText(
            String elementNs, String elementLocal, String childNs, String childLocal) {
        return byChildText(NameRef.of(elementNs, elementLocal), NameRef.of(childNs, childLocal));
    }

    public NameRef element() {
        return element;
    }

    public boolean matchesElement(String namespaceUri, String localName) {
        return element.matches(namespaceUri, localName);
    }

    /**
     * @return key text when present; empty when the configured attribute/child is missing
     */
    public Optional<String> extractKey(Element el) {
        if (attribute != null) {
            return attributeKey(el);
        }
        return childTextKey(el);
    }

    private Optional<String> attributeKey(Element el) {
        var attr = attribute.namespaceUri().isEmpty()
                ? el.getAttributeNode(attribute.localName())
                : el.getAttributeNodeNS(attribute.namespaceUri(), attribute.localName());
        if (attr == null) {
            // unqualified attributes may still be stored with null NS
            if (!attribute.namespaceUri().isEmpty()) {
                return Optional.empty();
            }
            attr = el.getAttributeNodeNS(null, attribute.localName());
            if (attr == null) {
                return Optional.empty();
            }
        }
        return Optional.of(attr.getValue());
    }

    private Optional<String> childTextKey(Element el) {
        for (Node node = el.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childEl = (Element) node;
            String ns = childEl.getNamespaceURI() == null ? "" : childEl.getNamespaceURI();
            String local = childEl.getLocalName() != null
                    ? childEl.getLocalName()
                    : childEl.getNodeName();
            if (child.matches(ns, local)) {
                String text = childEl.getTextContent();
                return Optional.of(text == null ? "" : text.trim());
            }
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        if (attribute != null) {
            return "ElementMatch{element=" + element + ", attribute=" + attribute + "}";
        }
        return "ElementMatch{element=" + element + ", child=" + child + "}";
    }
}
