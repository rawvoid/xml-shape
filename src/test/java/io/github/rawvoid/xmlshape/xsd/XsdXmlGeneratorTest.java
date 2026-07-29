package io.github.rawvoid.xmlshape.xsd;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XsdXmlGeneratorTest {

    private static final String BASIC_NS = "http://example.com/basic";
    private static final String CHAMELEON_NS = "http://example.com/chameleon";
    private static final String SUBST_NS = "http://example.com/subst";
    private static final String EDIST_NS = "http://www.iata.org/IATA/EDIST";

    @Test
    void generatesAllElementsAttributesAndSampleValues() throws Exception {
        Path schema = resource("xsd/basic.xsd");
        String xml = XsdXmlGenerator.generate(schema, "Root");
        Document doc = parse(xml);
        Element root = doc.getDocumentElement();

        assertEquals("Root", root.getLocalName());
        assertEquals(BASIC_NS, root.getNamespaceURI());
        assertNotNull(root.getAttributeNode("id"));
        assertNotNull(root.getAttributeNode("flag"));
        assertEquals("prod", root.getAttribute("env"));
        assertEquals(BASIC_NS, child(root, "Required").getNamespaceURI());
        assertFalse(root.getAttributeNode("id").getNamespaceURI() != null
                && !root.getAttributeNode("id").getNamespaceURI().isEmpty());

        assertEquals("string", text(child(root, "Required")));
        assertEquals("string", text(child(root, "Optional")));
        assertEquals("OPEN", text(child(root, "Status")));

        Element amount = child(root, "Amount");
        assertEquals(0, new java.math.BigDecimal(text(amount)).compareTo(new java.math.BigDecimal("1")));
        assertEquals("string", amount.getAttribute("currency"));

        Element extended = child(root, "Extended");
        assertEquals("string", text(child(extended, "BaseField")));
        assertEquals("string", text(child(extended, "ExtraField")));
        assertEquals("1", extended.getAttribute("extraAttr"));
        assertNotNull(extended.getAttributeNode("baseAttr"));
    }

    @Test
    void firstChoiceSkipsNonViableOptionalWhenOptionalsExcluded() throws Exception {
        Path schema = resource("xsd/choice-first-optional.xsd");
        var options = GenerateOptions.defaults()
                .withChoiceStrategy(ChoiceStrategy.FIRST)
                .withIncludeOptionalElements(false);
        Document doc = parse(XsdXmlGenerator.generate(schema, "Root", options));
        Element root = doc.getDocumentElement();
        assertEquals(0, root.getElementsByTagNameNS("http://example.com/choiceopt", "OptionalBranch").getLength());
        assertNotNull(child(root, "RequiredBranch"));
    }

    @Test
    void firstChoiceUsesSchemaOrderWhenOptionalsIncluded() throws Exception {
        Path schema = resource("xsd/choice-first-optional.xsd");
        Document doc = parse(XsdXmlGenerator.generate(
                schema, "Root", GenerateOptions.defaults().withChoiceStrategy(ChoiceStrategy.FIRST)));
        Element root = doc.getDocumentElement();
        assertNotNull(child(root, "OptionalBranch"));
        assertEquals(0, root.getElementsByTagNameNS("http://example.com/choiceopt", "RequiredBranch").getLength());
    }

    @Test
    void choiceStrategyAllVsFirst() throws Exception {
        Path schema = resource("xsd/basic.xsd");

        Document all = parse(XsdXmlGenerator.generate(schema, "Root", GenerateOptions.defaults()));
        Element choiceAll = child(all.getDocumentElement(), "ChoiceBox");
        assertNotNull(child(choiceAll, "BranchA"));
        assertNotNull(child(choiceAll, "BranchB"));

        Document first = parse(XsdXmlGenerator.generate(
                schema, "Root", GenerateOptions.defaults().withChoiceStrategy(ChoiceStrategy.FIRST)));
        Element choiceFirst = child(first.getDocumentElement(), "ChoiceBox");
        assertNotNull(child(choiceFirst, "BranchA"));
        assertEquals(0, choiceFirst.getElementsByTagNameNS(BASIC_NS, "BranchB").getLength());
    }

    @Test
    void optionalElementsCanBeSkipped() throws Exception {
        Path schema = resource("xsd/basic.xsd");
        var options = GenerateOptions.defaults()
                .withIncludeOptionalElements(false)
                .withIncludeOptionalAttributes(false);
        Document doc = parse(XsdXmlGenerator.generate(schema, "Root", options));
        Element root = doc.getDocumentElement();
        assertEquals(0, root.getElementsByTagNameNS(BASIC_NS, "Optional").getLength());
        assertTrue(root.getAttribute("flag").isEmpty() || root.getAttributeNode("flag") == null);
        assertNotNull(root.getAttributeNode("id"));
    }

    @Test
    void chameleonIncludeUsesTargetNamespace() throws Exception {
        Path schema = resource("xsd/chameleon-main.xsd");
        Document doc = parse(XsdXmlGenerator.generate(schema, "Wrapper"));
        Element root = doc.getDocumentElement();
        assertEquals(CHAMELEON_NS, root.getNamespaceURI());
        assertEquals(CHAMELEON_NS, child(root, "Shared").getNamespaceURI());
        Element local = child(root, "Local");
        assertEquals(CHAMELEON_NS, local.getNamespaceURI());
        assertEquals(CHAMELEON_NS, child(local, "SharedChild").getNamespaceURI());
    }

    @Test
    void recursionStopsOnCycle() throws Exception {
        Path schema = resource("xsd/recursive.xsd");
        Document doc = parse(XsdXmlGenerator.generate(schema, "Node"));
        // Root expands fully (Label + one Child). Child shares NodeType → shell only.
        int nodes = countLocalNames(doc.getDocumentElement(), "Child");
        assertEquals(1, nodes);
        Element root = doc.getDocumentElement();
        assertEquals("string", text(child(root, "Label")));
        Element nested = child(root, "Child");
        assertEquals(0, nested.getElementsByTagNameNS("*", "Child").getLength());
        assertEquals(0, nested.getElementsByTagNameNS("*", "Label").getLength());
    }

    @Test
    void sameLocalNameDifferentTypesCanNest() throws Exception {
        Path schema = resource("xsd/same-name-diff-type.xsd");
        Document doc = parse(XsdXmlGenerator.generate(schema, "Root"));
        Element outerItem = child(doc.getDocumentElement(), "Item");
        Element innerItem = child(outerItem, "Item");
        assertEquals("string", text(innerItem));
    }

    @Test
    void missingRootThrows() {
        Path schema = resource("xsd/basic.xsd");
        assertThrows(XsdXmlGenerationException.class,
                () -> XsdXmlGenerator.generate(schema, "DoesNotExist"));
    }

    @Test
    void schemaLoadErrorsAreReported() {
        Path schema = resource("xsd/broken-syntax.xsd");
        var ex = assertThrows(XsdXmlGenerationException.class,
                () -> XsdXmlGenerator.generate(schema, "Root"));
        assertTrue(ex.getMessage().contains("Failed to load schema"));
        assertTrue(ex.getMessage().contains("src-resolve") || ex.getMessage().contains("DoesNotExistType"));
    }

    @Test
    void idrefSamplesBindToGeneratedIds() throws Exception {
        Path schema = resource("xsd/idref.xsd");
        Document doc = parse(XsdXmlGenerator.generate(schema, "Root"));
        Element root = doc.getDocumentElement();
        String id = child(root, "Target").getAttribute("id");
        String ref = child(root, "Pointer").getAttribute("ref");
        assertFalse(id.isBlank());
        assertEquals(id, ref);
    }

    @Test
    void idrefBeforeIdStillBinds() throws Exception {
        Path schema = resource("xsd/idref-before-id.xsd");
        Document doc = parse(XsdXmlGenerator.generate(schema, "Root"));
        Element root = doc.getDocumentElement();
        String ref = child(root, "Pointer").getAttribute("ref");
        String id = child(root, "Target").getAttribute("id");
        assertFalse(id.isBlank());
        assertEquals(ref, id);
    }

    @Test
    void namespacedAttributesUsePrefixes() throws Exception {
        Path schema = resource("xsd/qualified-attr.xsd");
        Document doc = parse(XsdXmlGenerator.generate(schema, "Root"));
        Element root = doc.getDocumentElement();
        assertEquals("string", root.getAttributeNS("http://example.com/qattr", "code"));
        assertNotNull(root.getAttributeNodeNS("http://example.com/qattr", "localFlag"));
        assertEquals("true", root.getAttributeNS("http://example.com/qattr", "localFlag"));
    }

    @Test
    void wildcardPlaceholdersAreOptional() throws Exception {
        Path schema = resource("xsd/wildcard.xsd");
        Document off = parse(XsdXmlGenerator.generate(schema, "Root"));
        assertEquals(0, off.getElementsByTagNameNS(
                "http://rawvoid.github.io/xml-shape/wildcard", "any").getLength());

        Document on = parse(XsdXmlGenerator.generate(schema, "Root",
                GenerateOptions.defaults().withEmitWildcardPlaceholders(true)));
        Element root = on.getDocumentElement();
        assertEquals(1, root.getElementsByTagNameNS(
                "http://rawvoid.github.io/xml-shape/wildcard", "any").getLength());
        assertNotNull(root.getAttributeNodeNS("http://rawvoid.github.io/xml-shape/wildcard", "anyAttr"));
    }

    @Test
    void honorsCommonSimpleTypeFacets() throws Exception {
        Path schema = resource("xsd/facets.xsd");
        Document doc = parse(XsdXmlGenerator.generate(schema, "Root"));
        Element root = doc.getDocumentElement();
        assertEquals(3, text(child(root, "Code")).length());
        assertTrue(text(child(root, "Short")).length() <= 2);
        assertTrue(text(child(root, "MinTen")).length() >= 10);
        int bounded = Integer.parseInt(text(child(root, "Bounded")));
        assertTrue(bounded >= 5 && bounded <= 9);
    }

    @Test
    void binaryLengthFacetsUseOctets() throws Exception {
        Path schema = resource("xsd/binary-length.xsd");
        Document doc = parse(XsdXmlGenerator.generate(schema, "Root"));
        Element root = doc.getDocumentElement();
        String hex = text(child(root, "HexTwo"));
        assertEquals(4, hex.length());
        assertTrue(hex.matches("[0-9A-Fa-f]{4}"));
        byte[] decoded = java.util.Base64.getDecoder().decode(text(child(root, "B64Three")));
        assertEquals(3, decoded.length);
    }

    @Test
    void exclusiveNumericBoundsStayInRange() throws Exception {
        Path schema = resource("xsd/numeric-exclusive.xsd");
        Document doc = parse(XsdXmlGenerator.generate(schema, "Root"));
        Element root = doc.getDocumentElement();
        var open = new java.math.BigDecimal(text(child(root, "OpenUnit")));
        assertTrue(open.compareTo(java.math.BigDecimal.ZERO) > 0);
        assertTrue(open.compareTo(java.math.BigDecimal.ONE) < 0);
        int hundred = Integer.parseInt(text(child(root, "AtLeastHundred")));
        assertTrue(hundred >= 100 && hundred <= 999);
    }

    @Test
    void ambiguousRootRequiresNamespace() {
        Path schema = resource("xsd/multi-ns-a.xsd");
        var ex = assertThrows(XsdXmlGenerationException.class,
                () -> XsdXmlGenerator.generate(schema, "Payload"));
        assertTrue(ex.getMessage().contains("Ambiguous"));

        String xmlA = XsdXmlGenerator.generate(schema, "Payload", "http://example.com/a",
                GenerateOptions.defaults());
        assertTrue(xmlA.contains("http://example.com/a"));

        String xmlB = XsdXmlGenerator.generate(schema, "Payload", "http://example.com/b",
                GenerateOptions.defaults());
        assertTrue(xmlB.contains("http://example.com/b"));
    }

    @Test
    void expandsSubstitutionGroupForAbstractElements() throws Exception {
        Path schema = resource("xsd/substitution.xsd");

        Document all = parse(XsdXmlGenerator.generate(schema, "Root", GenerateOptions.defaults()));
        Element rootAll = all.getDocumentElement();
        assertEquals(0, rootAll.getElementsByTagNameNS(SUBST_NS, "Animal").getLength());
        assertNotNull(child(rootAll, "Cat"));
        assertNotNull(child(rootAll, "Dog"));

        Document first = parse(XsdXmlGenerator.generate(
                schema, "Root", GenerateOptions.defaults().withChoiceStrategy(ChoiceStrategy.FIRST)));
        Element rootFirst = first.getDocumentElement();
        assertNotNull(child(rootFirst, "Cat"));
        assertEquals(0, rootFirst.getElementsByTagNameNS(SUBST_NS, "Dog").getLength());
    }

    @Test
    void skipsAbstractElementsWithoutSubstitutionMembers() throws Exception {
        Path schema = resource("xsd/abstract-no-subst.xsd");
        String ns = "http://example.com/abstract-no-subst";

        Document doc = parse(XsdXmlGenerator.generate(schema, "Root"));
        Element root = doc.getDocumentElement();

        assertEquals(0, root.getElementsByTagNameNS(ns, "OrphanAbstract").getLength());
        assertNotNull(child(root, "Concrete"));
        assertEquals("string", text(child(root, "Concrete")));
    }

    @Test
    void airShoppingRsIntegration() throws Exception {
        Path schema = resource("schemas/AirShoppingRS.xsd");
        String xml = XsdXmlGenerator.generate(schema, "AirShoppingRS");
        Document doc = parse(xml);
        Element root = doc.getDocumentElement();

        assertEquals("AirShoppingRS", root.getLocalName());
        assertEquals(EDIST_NS, root.getNamespaceURI());
        assertNotNull(root.getAttributeNode("Version"));
        assertNotNull(root.getAttributeNode("TimeStamp"));
        assertEquals("Production", root.getAttribute("Target"));

        assertTrue(root.getElementsByTagNameNS(EDIST_NS, "Document").getLength() >= 1);
        assertTrue(root.getElementsByTagNameNS(EDIST_NS, "Success").getLength() >= 1);
        assertTrue(root.getElementsByTagNameNS(EDIST_NS, "OffersGroup").getLength() >= 1);
        // Errors branch of the root choice should also be present under ALL strategy.
        assertTrue(root.getElementsByTagNameNS(EDIST_NS, "Errors").getLength() >= 1
                || root.getElementsByTagNameNS(EDIST_NS, "Error").getLength() >= 1);

        // Local attributes are unqualified.
        assertTrue(root.getAttributeNode("Version").getNamespaceURI() == null
                || root.getAttributeNode("Version").getNamespaceURI().isEmpty());
    }

    private static Path resource(String name) {
        try {
            var url = XsdXmlGeneratorTest.class.getClassLoader().getResource(name);
            assertNotNull(url, "missing test resource: " + name);
            return Paths.get(url.toURI());
        } catch (Exception e) {
            throw new AssertionError("Cannot resolve test resource: " + name, e);
        }
    }

    private static Document parse(String xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static Element child(Element parent, String localName) {
        NodeList list = parent.getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            if (list.item(i) instanceof Element el && localName.equals(el.getLocalName())) {
                return el;
            }
        }
        throw new AssertionError("Child element not found: " + localName + " under " + parent.getLocalName());
    }

    private static String text(Element element) {
        return element.getTextContent() == null ? "" : element.getTextContent().trim();
    }

    private static int countLocalNames(Element root, String localName) {
        return root.getElementsByTagNameNS("*", localName).getLength();
    }
}
