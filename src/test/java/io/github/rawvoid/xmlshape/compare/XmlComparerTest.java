package io.github.rawvoid.xmlshape.compare;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XmlComparerTest {

    @Test
    void identicalDocumentsAreEqual() {
        String xml = """
                <Root xmlns="http://example.com">
                  <Item id="1">alpha</Item>
                </Root>
                """;
        assertTrue(XmlComparer.compare(xml, xml).isEqual());
    }

    @Test
    void differentPrefixesSameNamespaceAreEqual() {
        String expected = """
                <a:Root xmlns:a="http://example.com">
                  <a:Item>x</a:Item>
                </a:Root>
                """;
        String actual = """
                <b:Root xmlns:b="http://example.com">
                  <b:Item>x</b:Item>
                </b:Root>
                """;
        assertTrue(XmlComparer.compare(expected, actual).isEqual());
    }

    @Test
    void namespaceUriMismatchIsReported() {
        String expected = "<Root xmlns=\"http://example.com/a\"/>";
        String actual = "<Root xmlns=\"http://example.com/b\"/>";
        var diff = XmlComparer.compare(expected, actual);
        assertFalse(diff.isEqual());
        assertTrue(types(diff).contains(DifferenceType.ELEMENT_NAMESPACE));
    }

    @Test
    void elementNameMismatchIsReported() {
        var diff = XmlComparer.compare("<Root/>", "<Other/>");
        assertFalse(diff.isEqual());
        assertTrue(types(diff).contains(DifferenceType.ELEMENT_NAME));
    }

    @Test
    void textValueMismatchIsReported() {
        var diff = XmlComparer.compare("<Root>10.00</Root>", "<Root>10</Root>");
        assertFalse(diff.isEqual());
        assertEquals(1, diff.differences().size());
        assertEquals(DifferenceType.TEXT_VALUE, diff.differences().getFirst().type());
        assertEquals("/Root[1]", diff.differences().getFirst().path());
    }

    @Test
    void whitespaceDifferencesIgnoredByDefault() {
        String expected = "<Root><Item>hello world</Item></Root>";
        String actual = """
                <Root>
                  <Item>
                    hello   world
                  </Item>
                </Root>
                """;
        assertTrue(XmlComparer.compare(expected, actual).isEqual());
    }

    @Test
    void whitespaceDifferencesDetectedWhenNotIgnored() {
        String expected = "<Root>hello world</Root>";
        String actual = "<Root>hello   world</Root>";
        var options = CompareOptions.defaults().withIgnoreWhitespace(false);
        var diff = XmlComparer.compare(expected, actual, options);
        assertFalse(diff.isEqual());
        assertTrue(types(diff).contains(DifferenceType.TEXT_VALUE));
    }

    @Test
    void attributeValueMismatchIsReported() {
        var diff = XmlComparer.compare("<Root id=\"1\"/>", "<Root id=\"2\"/>");
        assertFalse(diff.isEqual());
        assertEquals(DifferenceType.ATTRIBUTE_VALUE, diff.differences().getFirst().type());
        assertTrue(diff.differences().getFirst().path().endsWith("/@id"));
    }

    @Test
    void missingAndUnexpectedAttributesAreReported() {
        var diff = XmlComparer.compare("<Root a=\"1\" b=\"2\"/>", "<Root a=\"1\" c=\"3\"/>");
        assertFalse(diff.isEqual());
        assertTrue(types(diff).contains(DifferenceType.ATTRIBUTE_MISSING));
        assertTrue(types(diff).contains(DifferenceType.ATTRIBUTE_UNEXPECTED));
    }

    @Test
    void xmlnsDeclarationFormDoesNotAffectEquality() {
        String expected = "<e:Root xmlns:e=\"http://example.com\" e:flag=\"true\"/>";
        String actual = "<Root xmlns=\"http://example.com\" xmlns:e=\"http://example.com\" e:flag=\"true\"/>";
        // default ns on Root vs prefixed - both have namespace http://example.com for Root
        // attribute e:flag has ns http://example.com on both
        assertTrue(XmlComparer.compare(expected, actual).isEqual());
    }

    @Test
    void childOrderMattersWhenOrderSensitive() {
        String expected = "<Root><A/><B/></Root>";
        String actual = "<Root><B/><A/></Root>";
        var diff = XmlComparer.compare(expected, actual, CompareOptions.defaults());
        assertFalse(diff.isEqual());
    }

    @Test
    void childOrderIgnoredWhenOrderInsensitive() {
        String expected = "<Root><A>1</A><B>2</B></Root>";
        String actual = "<Root><B>2</B><A>1</A></Root>";
        var options = CompareOptions.defaults().withOrderSensitive(false);
        assertTrue(XmlComparer.compare(expected, actual, options).isEqual());
    }

    @Test
    void sameNameSiblingCountMismatchIsReported() {
        String expected = "<Root><Item/><Item/></Root>";
        String actual = "<Root><Item/></Root>";
        var diff = XmlComparer.compare(expected, actual);
        assertFalse(diff.isEqual());
        assertTrue(types(diff).contains(DifferenceType.ELEMENT_MISSING));
    }

    @Test
    void unexpectedElementIsReported() {
        String expected = "<Root><Item/></Root>";
        String actual = "<Root><Item/><Extra/></Root>";
        var diff = XmlComparer.compare(expected, actual);
        assertFalse(diff.isEqual());
        assertTrue(types(diff).contains(DifferenceType.ELEMENT_UNEXPECTED));
    }

    @Test
    void invalidXmlThrows() {
        assertThrows(XmlCompareException.class,
                () -> XmlComparer.compare("<Root>", "<Root/>"));
        assertThrows(XmlCompareException.class,
                () -> XmlComparer.compare("<Root/>", "not-xml"));
    }

    @Test
    void nullInputThrows() {
        assertThrows(XmlCompareException.class,
                () -> XmlComparer.compare(null, "<Root/>"));
    }

    @Test
    void junitStyleAssertPassesWhenEqual() {
        var diff = XmlComparer.compare("<Root/>", "<Root></Root>");
        assertTrue(diff.isEqual(), diff::failureMessage);
    }

    @Test
    void failureMessageContainsPathAndExpectedText() {
        var diff = XmlComparer.compare(
                "<Root><Price>10.00</Price></Root>",
                "<Root><Price>10</Price></Root>");
        assertFalse(diff.isEqual());
        String message = diff.failureMessage();
        assertTrue(message.contains("/Root[1]/Price[1]"));
        assertTrue(message.contains("10.00"));
    }

    @Test
    void junitStyleAssertNotEqual() {
        var diff = XmlComparer.compare("<Root>a</Root>", "<Root>b</Root>");
        assertFalse(diff.isEqual(), "documents were expected to differ");
    }

    @Test
    void orderInsensitiveSameNameSiblingsPairedInDocumentOrder() {
        String expected = """
                <Root>
                  <Item id="1">a</Item>
                  <Item id="2">b</Item>
                </Root>
                """;
        String actual = """
                <Root>
                  <Item id="2">b</Item>
                  <Item id="1">a</Item>
                </Root>
                """;
        // order-insensitive groups by name; within Item group, first expected pairs with first actual
        var options = CompareOptions.defaults().withOrderSensitive(false);
        var diff = XmlComparer.compare(expected, actual, options);
        assertFalse(diff.isEqual());
        assertTrue(types(diff).contains(DifferenceType.ATTRIBUTE_VALUE)
                || types(diff).contains(DifferenceType.TEXT_VALUE));
    }

    @Test
    void namespacedAttributesAreComparedByUri() {
        String expected = """
                <Root xmlns:a="http://example.com/a" a:code="X"/>
                """;
        String actual = """
                <Root xmlns:b="http://example.com/a" b:code="X"/>
                """;
        assertTrue(XmlComparer.compare(expected, actual).isEqual());

        String wrongNs = """
                <Root xmlns:b="http://example.com/other" b:code="X"/>
                """;
        var diff = XmlComparer.compare(expected, wrongNs);
        assertFalse(diff.isEqual());
        assertTrue(types(diff).contains(DifferenceType.ATTRIBUTE_MISSING)
                || types(diff).contains(DifferenceType.ATTRIBUTE_UNEXPECTED));
    }

    @Test
    void collectMultipleDifferences() {
        String expected = "<Root a=\"1\"><Child>x</Child></Root>";
        String actual = "<Root a=\"2\"><Child>y</Child></Root>";
        var diff = XmlComparer.compare(expected, actual);
        assertEquals(2, diff.differences().size());
    }

    @Test
    void structureOnlyIgnoresTextAndAttributeValues() {
        String expected = """
                <Root id="1" flag="true">
                  <Item>alpha</Item>
                  <Note>hello</Note>
                </Root>
                """;
        String actual = """
                <Root id="99" flag="false">
                  <Item>beta</Item>
                  <Note>world</Note>
                </Root>
                """;
        assertTrue(XmlComparer.compare(expected, actual, CompareOptions.structureOnly()).isEqual());
        assertTrue(XmlComparer.compare(expected, actual,
                CompareOptions.defaults().withCompareValues(false)).isEqual());
    }

    @Test
    void structureOnlyStillDetectsMissingAttribute() {
        var diff = XmlComparer.compare(
                "<Root id=\"1\"/>",
                "<Root/>",
                CompareOptions.structureOnly());
        assertFalse(diff.isEqual());
        assertTrue(types(diff).contains(DifferenceType.ATTRIBUTE_MISSING));
    }

    @Test
    void structureOnlyStillDetectsMissingChildElement() {
        var diff = XmlComparer.compare(
                "<Root><Item/><Extra/></Root>",
                "<Root><Item/></Root>",
                CompareOptions.structureOnly());
        assertFalse(diff.isEqual());
        assertTrue(types(diff).contains(DifferenceType.ELEMENT_MISSING));
    }

    @Test
    void structureOnlyStillRespectsChildOrderWhenOrderSensitive() {
        var diff = XmlComparer.compare(
                "<Root><A/><B/></Root>",
                "<Root><B/><A/></Root>",
                CompareOptions.structureOnly());
        assertFalse(diff.isEqual());
    }

    @Test
    void structureOnlyIgnoresTextWhenMixedWithElements() {
        String expected = "<Root>prefix<Child/>suffix</Root>";
        String actual = "<Root><Child/></Root>";
        assertTrue(XmlComparer.compare(expected, actual, CompareOptions.structureOnly()).isEqual());
    }

    @Test
    void withoutValueEqualityNumericLiteralsDiffer() {
        var diff = XmlComparer.compare("<Root>10</Root>", "<Root>10.0</Root>");
        assertFalse(diff.isEqual());
        assertTrue(types(diff).contains(DifferenceType.TEXT_VALUE));
    }

    @Test
    void numericValueEqualityTreatsEquivalentDecimalsAsEqual() {
        var options = CompareOptions.defaults().withValueEquality(ValueEqualities.numeric());
        assertTrue(XmlComparer.compare("<Root>10</Root>", "<Root>10.0</Root>", options).isEqual());
        assertTrue(XmlComparer.compare("<Root>10</Root>", "<Root>10.00</Root>", options).isEqual());
        assertTrue(XmlComparer.compare("<A amount=\"1.5\"/>", "<A amount=\"1.50\"/>", options).isEqual());
    }

    @Test
    void dateTimeValueEqualityMatchesSameInstantDifferentOffset() {
        var options = CompareOptions.defaults().withValueEquality(ValueEqualities.dateTime());
        assertTrue(XmlComparer.compare(
                "<Root>2020-01-01T00:00:00Z</Root>",
                "<Root>2020-01-01T08:00:00+08:00</Root>",
                options).isEqual());
    }

    @Test
    void dateTimeValueEqualityMatchesLocalDates() {
        var options = CompareOptions.defaults().withValueEquality(ValueEqualities.dateTime());
        assertTrue(XmlComparer.compare("<Root>2020-01-01</Root>", "<Root>2020-01-01</Root>", options).isEqual());
    }

    @Test
    void dateTimeValueEqualityMatchesLocalTimes() {
        var options = CompareOptions.defaults().withValueEquality(ValueEqualities.dateTime());
        assertTrue(XmlComparer.compare("<Root>09:30:00</Root>", "<Root>09:30:00</Root>", options).isEqual());
        assertTrue(XmlComparer.compare("<Root>09:30:00.5</Root>", "<Root>09:30:00.500</Root>", options).isEqual());
        assertFalse(XmlComparer.compare("<Root>09:30:00</Root>", "<Root>09:30:01</Root>", options).isEqual());
    }

    @Test
    void dateTimeDoesNotEquateDateAndInstant() {
        var options = CompareOptions.defaults().withValueEquality(ValueEqualities.dateTime());
        var diff = XmlComparer.compare(
                "<Root>2020-01-01</Root>",
                "<Root>2020-01-01T00:00:00Z</Root>",
                options);
        assertFalse(diff.isEqual());
    }

    @Test
    void dateTimeIgnoreTimeZoneComparesWallClockFields() {
        var options = CompareOptions.defaults().withValueEquality(ValueEqualities.dateTime(true));
        // Same local fields, different offsets → equal when zone is ignored
        assertTrue(XmlComparer.compare(
                "<Root>2020-01-01T00:00:00Z</Root>",
                "<Root>2020-01-01T00:00:00+08:00</Root>",
                options).isEqual());
        // Zoned date-time vs plain local date-time with same fields
        assertTrue(XmlComparer.compare(
                "<Root>2020-01-01T12:30:00Z</Root>",
                "<Root>2020-01-01T12:30:00</Root>",
                options).isEqual());
        // Offset times compared by local time only
        assertTrue(XmlComparer.compare(
                "<Root>09:15:00Z</Root>",
                "<Root>09:15:00+08:00</Root>",
                options).isEqual());
        assertTrue(XmlComparer.compare(
                "<Root>09:15:00+08:00</Root>",
                "<Root>09:15:00</Root>",
                options).isEqual());
    }

    @Test
    void dateTimeZoneAwareDoesNotEquateDifferentInstantsWithSameLocalFields() {
        var zoneAware = CompareOptions.defaults().withValueEquality(ValueEqualities.dateTime(false));
        assertFalse(XmlComparer.compare(
                "<Root>2020-01-01T00:00:00Z</Root>",
                "<Root>2020-01-01T00:00:00+08:00</Root>",
                zoneAware).isEqual());
    }

    @Test
    void durationValueEqualityMatchesEquivalentIsoDurations() {
        var options = CompareOptions.defaults().withValueEquality(ValueEqualities.duration());
        assertTrue(XmlComparer.compare("<Root>P1D</Root>", "<Root>PT24H</Root>", options).isEqual());
        assertTrue(XmlComparer.compare("<Root>PT1H30M</Root>", "<Root>PT90M</Root>", options).isEqual());
        assertFalse(XmlComparer.compare("<Root>P1D</Root>", "<Root>P2D</Root>", options).isEqual());
        // Year/month forms are not Duration-parseable → fall back to literal
        assertFalse(XmlComparer.compare("<Root>P1Y</Root>", "<Root>P12M</Root>", options).isEqual());
    }

    @Test
    void boolValueEquality() {
        var options = CompareOptions.defaults().withValueEquality(ValueEqualities.bool());
        assertTrue(XmlComparer.compare("<Root>true</Root>", "<Root>1</Root>", options).isEqual());
        assertTrue(XmlComparer.compare("<Root flag=\"false\"/>", "<Root flag=\"0\"/>", options).isEqual());
    }

    @Test
    void forLocalNamesLimitsTypedEquality() {
        var options = CompareOptions.defaults().withValueEquality(
                ValueEqualities.forLocalNames(ValueEqualities.numeric(), "Amount"));
        assertTrue(XmlComparer.compare(
                "<Root><Amount>10</Amount><Id>001</Id></Root>",
                "<Root><Amount>10.0</Amount><Id>001</Id></Root>",
                options).isEqual());
        var idDiff = XmlComparer.compare(
                "<Root><Amount>10</Amount><Id>001</Id></Root>",
                "<Root><Amount>10.0</Amount><Id>1</Id></Root>",
                options);
        assertFalse(idDiff.isEqual());
        assertTrue(types(idDiff).contains(DifferenceType.TEXT_VALUE));
    }

    @Test
    void commonTypesSmoke() {
        var options = CompareOptions.defaults().withValueEquality(ValueEqualities.commonTypes());
        // dateTime(true): wall-clock match with different offsets; duration / numeric / bool also typed
        assertTrue(XmlComparer.compare(
                "<Root><N>10</N><T>2020-01-01T00:00:00Z</T><D>P1D</D><B>true</B></Root>",
                "<Root><N>10.0</N><T>2020-01-01T00:00:00+08:00</T><D>PT24H</D><B>1</B></Root>",
                options).isEqual());
        // same Instant, different local fields → not equal under ignore-zone commonTypes
        assertFalse(XmlComparer.compare(
                "<Root>2020-01-01T00:00:00Z</Root>",
                "<Root>2020-01-01T08:00:00+08:00</Root>",
                options).isEqual());
    }

    @Test
    void structureOnlyDoesNotUseValueEquality() {
        var options = CompareOptions.structureOnly().withValueEquality(ValueEqualities.numeric());
        // structure equal despite different text; values not compared
        assertTrue(XmlComparer.compare("<Root>10</Root>", "<Root>99</Root>", options).isEqual());
    }

    private static EnumSet<DifferenceType> types(XmlDiff diff) {
        return diff.differences().stream()
                .map(Difference::type)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DifferenceType.class)));
    }
}
