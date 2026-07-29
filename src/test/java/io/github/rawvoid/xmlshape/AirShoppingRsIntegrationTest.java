package io.github.rawvoid.xmlshape;

import io.github.rawvoid.xmlshape.compare.CompareOptions;
import io.github.rawvoid.xmlshape.compare.XmlComparer;
import io.github.rawvoid.xmlshape.xsd.GenerateOptions;
import io.github.rawvoid.xmlshape.xsd.XsdXmlGenerator;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end: generate a large NDC sample from XSD, then compare the result to itself.
 */
class AirShoppingRsIntegrationTest {

    private static final String ROOT = "AirShoppingRS";
    private static final String EDIST_NS = "http://www.iata.org/IATA/EDIST";

    @Test
    void generatedAirShoppingRsComparesEqualToItself() {
        Path schema = resource("schemas/AirShoppingRS.xsd");
        String xml = XsdXmlGenerator.generate(schema, ROOT);

        assertNotNull(xml);
        assertFalse(xml.isBlank());
        assertTrue(xml.contains(ROOT), "generated XML should contain root element name");

        var diff = XmlComparer.compare(xml, xml);
        assertTrue(diff.isEqual(), diff::failureMessage);
    }

    @Test
    void twoIndependentGenerationsCompareEqual() {
        Path schema = resource("schemas/AirShoppingRS.xsd");
        String first = XsdXmlGenerator.generate(schema, ROOT, EDIST_NS, GenerateOptions.defaults());
        String second = XsdXmlGenerator.generate(schema, ROOT, EDIST_NS, GenerateOptions.defaults());

        var diff = XmlComparer.compare(first, second);
        assertTrue(diff.isEqual(), diff::failureMessage);
    }

    @Test
    void generatedAirShoppingRsStructureOnlySelfCompare() {
        Path schema = resource("schemas/AirShoppingRS.xsd");
        String xml = XsdXmlGenerator.generate(schema, ROOT);

        var diff = XmlComparer.compare(xml, xml, CompareOptions.structureOnly());
        assertTrue(diff.isEqual(), diff::failureMessage);

        var unordered = XmlComparer.compare(xml, xml,
                CompareOptions.structureOnly().withOrderSensitive(false));
        assertTrue(unordered.isEqual(), unordered::failureMessage);
    }

    private static Path resource(String name) {
        try {
            var url = AirShoppingRsIntegrationTest.class.getClassLoader().getResource(name);
            assertNotNull(url, "missing test resource: " + name);
            return Paths.get(url.toURI());
        } catch (Exception e) {
            throw new AssertionError("Cannot resolve test resource: " + name, e);
        }
    }
}
