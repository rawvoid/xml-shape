package io.github.rawvoid.xmlshape.compare.internal;

import io.github.rawvoid.xmlshape.compare.CompareOptions;
import io.github.rawvoid.xmlshape.compare.Difference;
import io.github.rawvoid.xmlshape.compare.DifferenceType;
import io.github.rawvoid.xmlshape.compare.ElementMatch;
import io.github.rawvoid.xmlshape.compare.NameRef;
import io.github.rawvoid.xmlshape.compare.ValueContext;
import io.github.rawvoid.xmlshape.compare.ValueKind;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Recursively compares two DOM element trees and collects all differences.
 */
public final class StructuralComparator {
    private static final String XMLNS_NS = "http://www.w3.org/2000/xmlns/";

    private final CompareOptions options;
    private final List<NameRef> ignoredAttributes;
    private final List<NameRef> ignoredElements;
    private final List<Difference> differences = new ArrayList<>();

    public StructuralComparator(CompareOptions options) {
        this.options = Objects.requireNonNull(options, "options");
        this.ignoredAttributes = options.ignoredAttributes();
        this.ignoredElements = options.ignoredElements();
    }

    public List<Difference> compare(Element expected, Element actual) {
        compareElements(expected, actual, pathOf(expected, null, 1));
        return List.copyOf(differences);
    }

    private void compareElements(Element expected, Element actual, String path) {
        var expectedNs = namespaceOf(expected);
        var actualNs = namespaceOf(actual);
        var expectedName = localNameOf(expected);
        var actualName = localNameOf(actual);

        boolean nameMatch = expectedName.equals(actualName);
        boolean nsMatch = expectedNs.equals(actualNs);
        if (!nameMatch) {
            add(path, DifferenceType.ELEMENT_NAME, expectedName, actualName,
                    "element name expected \"" + expectedName + "\" but was \"" + actualName + "\"");
        }
        if (!nsMatch) {
            add(path, DifferenceType.ELEMENT_NAMESPACE, displayNs(expectedNs), displayNs(actualNs),
                    "element namespace expected " + displayNs(expectedNs) + " but was " + displayNs(actualNs));
        }
        if (!nameMatch || !nsMatch) {
            return;
        }

        compareAttributes(expected, actual, path);

        if (!options.elementMatches().isEmpty()) {
            compareChildrenWithMatches(expected, actual, path);
        } else if (options.orderSensitive()) {
            compareChildrenOrderSensitive(expected, actual, path);
        } else {
            compareChildrenOrderInsensitive(expected, actual, path);
        }
    }

    /**
     * Elements covered by {@link ElementMatch} are paired by key; remaining elements follow
     * orderSensitive / QName grouping; texts are compared separately (not interleaved).
     */
    private void compareChildrenWithMatches(Element expected, Element actual, String path) {
        List<Element> expEls = elementChildren(expected);
        List<Element> actEls = elementChildren(actual);

        List<Element> expKeyed = new ArrayList<>();
        List<Element> actKeyed = new ArrayList<>();
        List<Element> expOther = new ArrayList<>();
        List<Element> actOther = new ArrayList<>();

        for (Element el : expEls) {
            if (findMatch(el).isPresent()) {
                expKeyed.add(el);
            } else {
                expOther.add(el);
            }
        }
        for (Element el : actEls) {
            if (findMatch(el).isPresent()) {
                actKeyed.add(el);
            } else {
                actOther.add(el);
            }
        }

        pairKeyedElements(expKeyed, actKeyed, path, expected, actual);

        if (options.orderSensitive()) {
            pairElementsInOrder(expOther, actOther, path);
        } else {
            pairElementsByQName(expOther, actOther, path, expected, actual);
        }

        if (options.compareValues()) {
            List<String> expectedTexts = textChildren(expected);
            List<String> actualTexts = textChildren(actual);
            if (options.orderSensitive()) {
                compareTextSequences(expected, expectedTexts, actualTexts, path);
            } else {
                compareTextMultisets(expected, expectedTexts, actualTexts, path);
            }
        }
    }

    private void pairKeyedElements(
            List<Element> expected, List<Element> actual, String path,
            Element expectedParent, Element actualParent) {
        Map<QName, ElementMatch> rulesByQName = new LinkedHashMap<>();
        for (ElementMatch match : options.elementMatches()) {
            rulesByQName.putIfAbsent(
                    new QName(match.element().namespaceUri(), match.element().localName()), match);
        }

        Map<QName, List<Element>> expGroups = groupByQName(expected);
        Map<QName, List<Element>> actGroups = groupByQName(actual);

        for (var entry : rulesByQName.entrySet()) {
            QName qName = entry.getKey();
            ElementMatch rule = entry.getValue();
            List<Element> expList = expGroups.getOrDefault(qName, List.of());
            List<Element> actList = actGroups.getOrDefault(qName, List.of());
            pairOneQNameByKey(expList, actList, rule, path, expectedParent, actualParent);
        }

        // Elements that looked keyed but QName had no rule should not happen; actGroups keys
        // without rules are handled as "other". Keyed lists only contain matching QNames.
    }

    private void pairOneQNameByKey(
            List<Element> expected, List<Element> actual, ElementMatch rule, String path,
            Element expectedParent, Element actualParent) {
        Map<String, List<Element>> expByKey = new LinkedHashMap<>();
        Map<String, List<Element>> actByKey = new LinkedHashMap<>();
        List<Element> expUnkeyed = new ArrayList<>();
        List<Element> actUnkeyed = new ArrayList<>();

        for (Element el : expected) {
            Optional<String> key = rule.extractKey(el);
            if (key.isPresent()) {
                expByKey.computeIfAbsent(key.get(), k -> new ArrayList<>()).add(el);
            } else {
                expUnkeyed.add(el);
            }
        }
        for (Element el : actual) {
            Optional<String> key = rule.extractKey(el);
            if (key.isPresent()) {
                actByKey.computeIfAbsent(key.get(), k -> new ArrayList<>()).add(el);
            } else {
                actUnkeyed.add(el);
            }
        }

        for (var entry : expByKey.entrySet()) {
            String key = entry.getKey();
            List<Element> expList = entry.getValue();
            List<Element> actList = actByKey.getOrDefault(key, List.of());
            int n = Math.min(expList.size(), actList.size());
            for (int i = 0; i < n; i++) {
                Element exp = expList.get(i);
                int index = siblingIndex(exp, expectedParent);
                compareElements(exp, actList.get(i),
                        path + "/" + localNameOf(exp) + "[" + index + "]");
            }
            for (int i = n; i < expList.size(); i++) {
                Element exp = expList.get(i);
                int index = siblingIndex(exp, expectedParent);
                add(path + "/" + localNameOf(exp) + "[" + index + "]", DifferenceType.ELEMENT_MISSING,
                        rule.element().toString(), null,
                        "missing element " + rule.element() + " key " + key);
            }
            for (int i = n; i < actList.size(); i++) {
                Element act = actList.get(i);
                int index = siblingIndex(act, actualParent);
                add(path + "/" + localNameOf(act) + "[" + index + "]", DifferenceType.ELEMENT_UNEXPECTED,
                        null, rule.element().toString(),
                        "unexpected element " + rule.element() + " key " + key);
            }
        }
        for (var entry : actByKey.entrySet()) {
            if (expByKey.containsKey(entry.getKey())) {
                continue;
            }
            for (Element act : entry.getValue()) {
                int index = siblingIndex(act, actualParent);
                add(path + "/" + localNameOf(act) + "[" + index + "]", DifferenceType.ELEMENT_UNEXPECTED,
                        null, rule.element().toString(),
                        "unexpected element " + rule.element() + " key " + entry.getKey());
            }
        }

        pairElementsInOrder(expUnkeyed, actUnkeyed, path);
    }

    private void pairElementsInOrder(List<Element> expected, List<Element> actual, String path) {
        int n = Math.min(expected.size(), actual.size());
        for (int i = 0; i < n; i++) {
            Element exp = expected.get(i);
            Element act = actual.get(i);
            Element parent = (Element) exp.getParentNode();
            int index = siblingIndex(exp, parent);
            compareElements(exp, act, path + "/" + localNameOf(exp) + "[" + index + "]");
        }
        for (int i = n; i < expected.size(); i++) {
            reportMissing(new Child.ElementChild(expected.get(i)), path);
        }
        for (int i = n; i < actual.size(); i++) {
            reportUnexpected(new Child.ElementChild(actual.get(i)), path);
        }
    }

    private void pairElementsByQName(
            List<Element> expected, List<Element> actual, String path,
            Element expectedParent, Element actualParent) {
        Map<QName, List<Element>> expectedGroups = groupByQName(expected);
        Map<QName, List<Element>> actualGroups = groupByQName(actual);

        for (var entry : expectedGroups.entrySet()) {
            var qName = entry.getKey();
            List<Element> expList = entry.getValue();
            List<Element> actList = actualGroups.getOrDefault(qName, List.of());
            int paired = Math.min(expList.size(), actList.size());
            for (int i = 0; i < paired; i++) {
                Element exp = expList.get(i);
                int index = siblingIndex(exp, expectedParent);
                compareElements(exp, actList.get(i), path + "/" + localNameOf(exp) + "[" + index + "]");
            }
            for (int i = paired; i < expList.size(); i++) {
                Element exp = expList.get(i);
                int index = siblingIndex(exp, expectedParent);
                add(path + "/" + localNameOf(exp) + "[" + index + "]", DifferenceType.ELEMENT_MISSING,
                        qName.display(), null, "missing element " + qName.display());
            }
            for (int i = expList.size(); i < actList.size(); i++) {
                Element act = actList.get(i);
                int index = siblingIndex(act, actualParent);
                add(path + "/" + localNameOf(act) + "[" + index + "]", DifferenceType.ELEMENT_UNEXPECTED,
                        null, qName.display(), "unexpected element " + qName.display());
            }
        }
        for (var entry : actualGroups.entrySet()) {
            if (expectedGroups.containsKey(entry.getKey())) {
                continue;
            }
            for (Element act : entry.getValue()) {
                int index = siblingIndex(act, actualParent);
                add(path + "/" + localNameOf(act) + "[" + index + "]", DifferenceType.ELEMENT_UNEXPECTED,
                        null, entry.getKey().display(),
                        "unexpected element " + entry.getKey().display());
            }
        }
    }

    private void compareTextSequences(
            Element parent, List<String> expected, List<String> actual, String path) {
        var ctx = textContext(parent, path);
        int n = Math.min(expected.size(), actual.size());
        for (int i = 0; i < n; i++) {
            if (!valuesEqual(ctx, expected.get(i), actual.get(i))) {
                add(path, DifferenceType.TEXT_VALUE, expected.get(i), actual.get(i),
                        "text expected \"" + expected.get(i) + "\" but was \"" + actual.get(i) + "\"");
            }
        }
        for (int i = n; i < expected.size(); i++) {
            add(path, DifferenceType.TEXT_VALUE, expected.get(i), null,
                    "missing text \"" + expected.get(i) + "\"");
        }
        for (int i = n; i < actual.size(); i++) {
            add(path, DifferenceType.TEXT_VALUE, null, actual.get(i),
                    "unexpected text \"" + actual.get(i) + "\"");
        }
    }

    private Optional<ElementMatch> findMatch(Element element) {
        String ns = namespaceOf(element);
        String local = localNameOf(element);
        for (ElementMatch match : options.elementMatches()) {
            if (match.matchesElement(ns, local)) {
                return Optional.of(match);
            }
        }
        return Optional.empty();
    }

    private void compareAttributes(Element expected, Element actual, String path) {
        Map<QName, String> expectedAttrs = attributesOf(expected);
        Map<QName, String> actualAttrs = attributesOf(actual);

        for (var entry : expectedAttrs.entrySet()) {
            var qName = entry.getKey();
            var actualValue = actualAttrs.get(qName);
            if (actualValue == null) {
                String detail = options.compareValues()
                        ? "attribute " + qName.display() + " missing, expected \"" + entry.getValue() + "\""
                        : "attribute " + qName.display() + " missing";
                add(path + attributePath(qName), DifferenceType.ATTRIBUTE_MISSING,
                        options.compareValues() ? entry.getValue() : null, null, detail);
            } else if (options.compareValues()) {
                var ctx = new ValueContext(ValueKind.ATTRIBUTE, qName.namespace(), qName.localName(),
                        path + attributePath(qName));
                if (!valuesEqual(ctx, entry.getValue(), actualValue)) {
                    add(path + attributePath(qName), DifferenceType.ATTRIBUTE_VALUE,
                            entry.getValue(), actualValue,
                            "attribute " + qName.display() + " expected \"" + entry.getValue()
                                    + "\" but was \"" + actualValue + "\"");
                }
            }
        }
        for (var entry : actualAttrs.entrySet()) {
            if (!expectedAttrs.containsKey(entry.getKey())) {
                String detail = options.compareValues()
                        ? "unexpected attribute " + entry.getKey().display()
                        + " with value \"" + entry.getValue() + "\""
                        : "unexpected attribute " + entry.getKey().display();
                add(path + attributePath(entry.getKey()), DifferenceType.ATTRIBUTE_UNEXPECTED,
                        null, options.compareValues() ? entry.getValue() : null, detail);
            }
        }
    }

    private void compareChildrenOrderSensitive(Element expected, Element actual, String path) {
        List<Child> expectedChildren = significantChildren(expected);
        List<Child> actualChildren = significantChildren(actual);
        int n = Math.min(expectedChildren.size(), actualChildren.size());
        for (int i = 0; i < n; i++) {
            compareChildPair(expected, expectedChildren.get(i), actualChildren.get(i), path, i);
        }
        for (int i = n; i < expectedChildren.size(); i++) {
            reportMissing(expectedChildren.get(i), path);
        }
        for (int i = n; i < actualChildren.size(); i++) {
            reportUnexpected(actualChildren.get(i), path);
        }
    }

    private void compareChildrenOrderInsensitive(Element expected, Element actual, String path) {
        List<Element> expectedElements = elementChildren(expected);
        List<Element> actualElements = elementChildren(actual);

        Map<QName, List<Element>> expectedGroups = groupByQName(expectedElements);
        Map<QName, List<Element>> actualGroups = groupByQName(actualElements);

        for (var entry : expectedGroups.entrySet()) {
            var qName = entry.getKey();
            List<Element> expList = entry.getValue();
            List<Element> actList = actualGroups.getOrDefault(qName, List.of());
            int paired = Math.min(expList.size(), actList.size());
            for (int i = 0; i < paired; i++) {
                Element exp = expList.get(i);
                int index = siblingIndex(exp, expected);
                compareElements(exp, actList.get(i), path + "/" + localNameOf(exp) + "[" + index + "]");
            }
            for (int i = paired; i < expList.size(); i++) {
                Element exp = expList.get(i);
                int index = siblingIndex(exp, expected);
                add(path + "/" + localNameOf(exp) + "[" + index + "]", DifferenceType.ELEMENT_MISSING,
                        qName.display(), null,
                        "missing element " + qName.display());
            }
        }
        for (var entry : actualGroups.entrySet()) {
            if (expectedGroups.containsKey(entry.getKey())) {
                continue;
            }
            for (Element act : entry.getValue()) {
                int index = siblingIndex(act, actual);
                add(path + "/" + localNameOf(act) + "[" + index + "]", DifferenceType.ELEMENT_UNEXPECTED,
                        null, entry.getKey().display(),
                        "unexpected element " + entry.getKey().display());
            }
        }
        for (var entry : expectedGroups.entrySet()) {
            List<Element> expList = entry.getValue();
            List<Element> actList = actualGroups.getOrDefault(entry.getKey(), List.of());
            for (int i = expList.size(); i < actList.size(); i++) {
                Element act = actList.get(i);
                int index = siblingIndex(act, actual);
                add(path + "/" + localNameOf(act) + "[" + index + "]", DifferenceType.ELEMENT_UNEXPECTED,
                        null, entry.getKey().display(),
                        "unexpected element " + entry.getKey().display());
            }
        }

        if (options.compareValues()) {
            List<String> expectedTexts = textChildren(expected);
            List<String> actualTexts = textChildren(actual);
            compareTextMultisets(expected, expectedTexts, actualTexts, path);
        }
    }

    private void compareChildPair(Element parent, Child expected, Child actual, String parentPath, int positionHint) {
        if (expected instanceof Child.ElementChild(Element expEl)
                && actual instanceof Child.ElementChild(Element actEl)) {
            int index = siblingIndex(expEl, (Element) expEl.getParentNode());
            String childPath = parentPath + "/" + localNameOf(expEl) + "[" + index + "]";
            compareElements(expEl, actEl, childPath);
            return;
        }
        if (expected instanceof Child.TextChild(String expText)
                && actual instanceof Child.TextChild(String actText)) {
            var ctx = textContext(parent, parentPath);
            if (!valuesEqual(ctx, expText, actText)) {
                add(parentPath, DifferenceType.TEXT_VALUE, expText, actText,
                        "text expected \"" + expText + "\" but was \"" + actText + "\"");
            }
            return;
        }
        add(parentPath, DifferenceType.NODE_TYPE, describe(expected), describe(actual),
                "node type expected " + describe(expected) + " but was " + describe(actual)
                        + " at child position " + (positionHint + 1));
    }

    private void reportMissing(Child child, String parentPath) {
        if (child instanceof Child.ElementChild(Element el)) {
            int index = siblingIndex(el, (Element) el.getParentNode());
            var qName = qNameOf(el);
            add(parentPath + "/" + localNameOf(el) + "[" + index + "]", DifferenceType.ELEMENT_MISSING,
                    qName.display(), null, "missing element " + qName.display());
        } else if (child instanceof Child.TextChild(String text)) {
            add(parentPath, DifferenceType.TEXT_VALUE, text, null,
                    "missing text \"" + text + "\"");
        }
    }

    private void reportUnexpected(Child child, String parentPath) {
        if (child instanceof Child.ElementChild(Element el)) {
            int index = siblingIndex(el, (Element) el.getParentNode());
            var qName = qNameOf(el);
            add(parentPath + "/" + localNameOf(el) + "[" + index + "]", DifferenceType.ELEMENT_UNEXPECTED,
                    null, qName.display(), "unexpected element " + qName.display());
        } else if (child instanceof Child.TextChild(String text)) {
            add(parentPath, DifferenceType.TEXT_VALUE, null, text,
                    "unexpected text \"" + text + "\"");
        }
    }

    private void compareTextMultisets(Element parent, List<String> expected, List<String> actual, String path) {
        var ctx = textContext(parent, path);
        List<String> remaining = new ArrayList<>(actual);
        for (String exp : expected) {
            int found = -1;
            for (int i = 0; i < remaining.size(); i++) {
                if (valuesEqual(ctx, exp, remaining.get(i))) {
                    found = i;
                    break;
                }
            }
            if (found >= 0) {
                remaining.remove(found);
            } else {
                add(path, DifferenceType.TEXT_VALUE, exp, null, "missing text \"" + exp + "\"");
            }
        }
        for (String act : remaining) {
            add(path, DifferenceType.TEXT_VALUE, null, act, "unexpected text \"" + act + "\"");
        }
    }

    private boolean valuesEqual(ValueContext context, String expected, String actual) {
        String left = prepareValue(expected);
        String right = prepareValue(actual);
        var equality = options.valueEquality();
        if (equality != null) {
            var decided = equality.equalTo(context, left, right);
            if (decided.isPresent()) {
                return decided.get();
            }
        }
        return left.equals(right);
    }

    private String prepareValue(String raw) {
        if (raw == null) {
            return "";
        }
        if (options.ignoreWhitespace()) {
            return normalizeText(raw);
        }
        return raw;
    }

    private static ValueContext textContext(Element parent, String path) {
        return new ValueContext(ValueKind.ELEMENT_TEXT, namespaceOf(parent), localNameOf(parent), path);
    }

    private List<Child> significantChildren(Element parent) {
        List<Child> children = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            switch (node.getNodeType()) {
                case Node.ELEMENT_NODE -> {
                    Element el = (Element) node;
                    if (!isIgnoredElement(el)) {
                        children.add(new Child.ElementChild(el));
                    }
                }
                case Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> {
                    if (!options.compareValues()) {
                        break;
                    }
                    String raw = node.getNodeValue();
                    if (raw == null) {
                        break;
                    }
                    if (options.ignoreWhitespace()) {
                        if (raw.isBlank()) {
                            break;
                        }
                        children.add(new Child.TextChild(normalizeText(raw)));
                    } else {
                        children.add(new Child.TextChild(raw));
                    }
                }
                default -> {
                    // comments, PIs, etc.
                }
            }
        }
        return children;
    }

    private List<Element> elementChildren(Element parent) {
        List<Element> elements = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element el = (Element) node;
                if (!isIgnoredElement(el)) {
                    elements.add(el);
                }
            }
        }
        return elements;
    }

    private List<String> textChildren(Element parent) {
        List<String> texts = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE) {
                String raw = node.getNodeValue();
                if (raw == null) {
                    continue;
                }
                if (options.ignoreWhitespace()) {
                    if (raw.isBlank()) {
                        continue;
                    }
                    texts.add(normalizeText(raw));
                } else {
                    texts.add(raw);
                }
            }
        }
        return texts;
    }

    private Map<QName, List<Element>> groupByQName(List<Element> elements) {
        Map<QName, List<Element>> groups = new LinkedHashMap<>();
        for (Element el : elements) {
            groups.computeIfAbsent(qNameOf(el), k -> new ArrayList<>()).add(el);
        }
        return groups;
    }

    private Map<QName, String> attributesOf(Element element) {
        Map<QName, String> map = new LinkedHashMap<>();
        var attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            var attr = (Attr) attrs.item(i);
            if (isNamespaceDeclaration(attr)) {
                continue;
            }
            var qName = qNameOf(attr);
            if (isIgnoredAttribute(qName)) {
                continue;
            }
            map.put(qName, attr.getValue());
        }
        return map;
    }

    private boolean isIgnoredAttribute(QName qName) {
        for (NameRef ref : ignoredAttributes) {
            if (ref.matches(qName.namespace(), qName.localName())) {
                return true;
            }
        }
        return false;
    }

    private boolean isIgnoredElement(Element element) {
        String ns = namespaceOf(element);
        String local = localNameOf(element);
        for (NameRef ref : ignoredElements) {
            if (ref.matches(ns, local)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNamespaceDeclaration(Attr attr) {
        String ns = attr.getNamespaceURI();
        if (XMLNS_NS.equals(ns)) {
            return true;
        }
        String name = attr.getName();
        return "xmlns".equals(name) || name.startsWith("xmlns:");
    }

    private String normalizeText(String text) {
        return text.trim().replaceAll("\\s+", " ");
    }

    private static String pathOf(Element element, String parentPath, int index) {
        String segment = "/" + localNameOf(element) + "[" + index + "]";
        return parentPath == null ? segment : parentPath + segment;
    }

    private static int siblingIndex(Element element, Element parent) {
        String ns = namespaceOf(element);
        String local = localNameOf(element);
        int index = 0;
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element sibling = (Element) node;
            if (namespaceOf(sibling).equals(ns) && localNameOf(sibling).equals(local)) {
                index++;
                if (sibling == element) {
                    return index;
                }
            }
        }
        return index;
    }

    private static QName qNameOf(Element element) {
        return new QName(namespaceOf(element), localNameOf(element));
    }

    private static QName qNameOf(Attr attr) {
        return new QName(namespaceOf(attr), localNameOf(attr));
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

    private static String attributePath(QName qName) {
        if (qName.namespace().isEmpty()) {
            return "/@" + qName.localName();
        }
        return "/@{" + qName.namespace() + "}" + qName.localName();
    }

    private static String displayNs(String ns) {
        return ns.isEmpty() ? "\"\"" : "\"" + ns + "\"";
    }

    private static String describe(Child child) {
        return switch (child) {
            case Child.ElementChild(Element el) -> "element " + qNameOf(el).display();
            case Child.TextChild(String text) -> "text \"" + text + "\"";
        };
    }

    private void add(String path, DifferenceType type, String expected, String actual, String message) {
        differences.add(new Difference(path, type, expected, actual, message));
    }

    private sealed interface Child {
        record ElementChild(Element element) implements Child {
        }

        record TextChild(String text) implements Child {
        }
    }

    private record QName(String namespace, String localName) {
        String display() {
            if (namespace.isEmpty()) {
                return localName;
            }
            return "{" + namespace + "}" + localName;
        }
    }
}
