package io.github.rawvoid.xmlshape.compare;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XmlComparerIgnoreTest {

    @Test
    void ignoreAttributesSkipsDynamicFields() {
        String expected = """
                <Root TimeStamp="2020-01-01T00:00:00Z" EchoToken="fixed" Version="1">
                  <Item>ok</Item>
                </Root>
                """;
        String actual = """
                <Root TimeStamp="2026-07-29T12:00:00Z" EchoToken="random" Version="1">
                  <Item>ok</Item>
                </Root>
                """;
        var options = CompareOptions.defaults()
                .withIgnoreAttributes(NameRef.local("TimeStamp"), NameRef.local("EchoToken"));
        assertTrue(XmlComparer.compare(expected, actual, options).isEqual());
    }

    @Test
    void ignoredAttributeStillAllowsOtherAttributeDiffs() {
        String expected = "<Root TimeStamp=\"a\" Version=\"1\"/>";
        String actual = "<Root TimeStamp=\"b\" Version=\"2\"/>";
        var options = CompareOptions.defaults().withIgnoreAttributes(NameRef.local("TimeStamp"));
        var diff = XmlComparer.compare(expected, actual, options);
        assertFalse(diff.isEqual());
        assertTrue(diff.differences().stream().anyMatch(d -> d.type() == DifferenceType.ATTRIBUTE_VALUE));
    }

    @Test
    void ignoreElementsDropsWholeSubtree() {
        String expected = """
                <Root>
                  <Keep>1</Keep>
                </Root>
                """;
        String actual = """
                <Root>
                  <Keep>1</Keep>
                  <Meta><Noise>x</Noise></Meta>
                </Root>
                """;
        var options = CompareOptions.defaults().withIgnoreElements(NameRef.local("Meta"));
        assertTrue(XmlComparer.compare(expected, actual, options).isEqual());
    }

    @Test
    void ignoreElementsOnExpectedSide() {
        String expected = """
                <Root>
                  <Keep>1</Keep>
                  <Meta>old</Meta>
                </Root>
                """;
        String actual = """
                <Root>
                  <Keep>1</Keep>
                </Root>
                """;
        var options = CompareOptions.defaults().withIgnoreElements(NameRef.local("Meta"));
        assertTrue(XmlComparer.compare(expected, actual, options).isEqual());
    }

    @Test
    void rootPathComparesOnlySubtree() {
        String expected = """
                <Root>
                  <Header TimeStamp="old"/>
                  <Body><Value>1</Value></Body>
                </Root>
                """;
        String actual = """
                <Root>
                  <Header TimeStamp="new"/>
                  <Body><Value>1</Value></Body>
                </Root>
                """;
        var options = CompareOptions.defaults().withRootPath("/Root[1]/Body[1]");
        assertTrue(XmlComparer.compare(expected, actual, options).isEqual());
    }

    @Test
    void rootPathDetectsSubtreeMismatch() {
        String expected = """
                <Root>
                  <Body><Value>1</Value></Body>
                </Root>
                """;
        String actual = """
                <Root>
                  <Body><Value>2</Value></Body>
                </Root>
                """;
        var options = CompareOptions.defaults().withRootPath("/Root[1]/Body[1]");
        var diff = XmlComparer.compare(expected, actual, options);
        assertFalse(diff.isEqual());
        assertTrue(diff.failureMessage().contains("Value"));
    }

    @Test
    void rootPathMissingThrows() {
        String xml = "<Root><Body/></Root>";
        var options = CompareOptions.defaults().withRootPath("/Root[1]/Missing[1]");
        assertThrows(XmlCompareException.class, () -> XmlComparer.compare(xml, xml, options));
    }

    @Test
    void namespacedIgnoreElement() {
        String ns = "http://example.com/a";
        String expected = """
                <r:Root xmlns:r="%s">
                  <r:Keep>1</r:Keep>
                </r:Root>
                """.formatted(ns);
        String actual = """
                <r:Root xmlns:r="%s">
                  <r:Keep>1</r:Keep>
                  <r:Meta>x</r:Meta>
                </r:Root>
                """.formatted(ns);
        var options = CompareOptions.defaults().withIgnoreElements(NameRef.of(ns, "Meta"));
        assertTrue(XmlComparer.compare(expected, actual, options).isEqual());
    }

    @Test
    void rootPathWithClarkNotation() {
        String ns = "http://example.com/a";
        String expected = """
                <r:Root xmlns:r="%s">
                  <r:Body><r:Value>1</r:Value></r:Body>
                </r:Root>
                """.formatted(ns);
        String actual = """
                <r:Root xmlns:r="%s">
                  <r:Noise>x</r:Noise>
                  <r:Body><r:Value>1</r:Value></r:Body>
                </r:Root>
                """.formatted(ns);
        var options = CompareOptions.defaults()
                .withRootPath("/{" + ns + "}Root[1]/{" + ns + "}Body[1]");
        assertTrue(XmlComparer.compare(expected, actual, options).isEqual());
    }
}
