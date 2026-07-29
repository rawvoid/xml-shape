package io.github.rawvoid.xmlshape.compare;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XmlComparerMatchTest {

    @Test
    void byAttributePairsRegardlessOfOrder() {
        String expected = """
                <Root>
                  <Item id="2">b</Item>
                  <Item id="1">a</Item>
                </Root>
                """;
        String actual = """
                <Root>
                  <Item id="1">a</Item>
                  <Item id="2">b</Item>
                </Root>
                """;
        // without match, order-insensitive still pairs by appearance order within QName
        assertFalse(XmlComparer.compare(expected, actual,
                CompareOptions.defaults().withOrderSensitive(false)).isEqual());

        var options = CompareOptions.defaults()
                .withElementMatches(ElementMatch.byAttribute("Item", "id"));
        assertTrue(XmlComparer.compare(expected, actual, options).isEqual());
    }

    @Test
    void byAttributeDetectsMissingKey() {
        String expected = """
                <Root>
                  <Item id="1">a</Item>
                  <Item id="2">b</Item>
                </Root>
                """;
        String actual = """
                <Root>
                  <Item id="1">a</Item>
                </Root>
                """;
        var options = CompareOptions.defaults()
                .withElementMatches(ElementMatch.byAttribute("Item", "id"));
        var diff = XmlComparer.compare(expected, actual, options);
        assertFalse(diff.isEqual());
        assertTrue(diff.failureMessage().contains("key 2")
                || diff.failureMessage().contains("missing"));
    }

    @Test
    void byAttributeDetectsValueMismatchOnPairedItem() {
        String expected = """
                <Root>
                  <Item id="1">a</Item>
                  <Item id="2">b</Item>
                </Root>
                """;
        String actual = """
                <Root>
                  <Item id="2">b</Item>
                  <Item id="1">wrong</Item>
                </Root>
                """;
        var options = CompareOptions.defaults()
                .withElementMatches(ElementMatch.byAttribute("Item", "id"));
        var diff = XmlComparer.compare(expected, actual, options);
        assertFalse(diff.isEqual());
        assertTrue(diff.failureMessage().contains("wrong") || diff.failureMessage().contains("a"));
    }

    @Test
    void byChildTextPairsPassengers() {
        String expected = """
                <Root>
                  <Passenger>
                    <PaxID>PAX2</PaxID>
                    <Name>Bob</Name>
                  </Passenger>
                  <Passenger>
                    <PaxID>PAX1</PaxID>
                    <Name>Alice</Name>
                  </Passenger>
                </Root>
                """;
        String actual = """
                <Root>
                  <Passenger>
                    <PaxID>PAX1</PaxID>
                    <Name>Alice</Name>
                  </Passenger>
                  <Passenger>
                    <PaxID>PAX2</PaxID>
                    <Name>Bob</Name>
                  </Passenger>
                </Root>
                """;
        var options = CompareOptions.defaults()
                .withElementMatches(ElementMatch.byChildText("Passenger", "PaxID"));
        assertTrue(XmlComparer.compare(expected, actual, options).isEqual());
    }

    @Test
    void matchCoexistsWithNonMatchedSiblings() {
        String expected = """
                <Root>
                  <Header>h</Header>
                  <Item id="2">b</Item>
                  <Item id="1">a</Item>
                  <Footer>f</Footer>
                </Root>
                """;
        String actual = """
                <Root>
                  <Header>h</Header>
                  <Item id="1">a</Item>
                  <Item id="2">b</Item>
                  <Footer>f</Footer>
                </Root>
                """;
        var options = CompareOptions.defaults()
                .withElementMatches(ElementMatch.byAttribute("Item", "id"));
        assertTrue(XmlComparer.compare(expected, actual, options).isEqual());
    }

    @Test
    void namespacedElementMatch() {
        String ns = "http://example.com/edist";
        String expected = """
                <r:Root xmlns:r="%s">
                  <r:Offer OfferID="OF-2"><r:Total>200</r:Total></r:Offer>
                  <r:Offer OfferID="OF-1"><r:Total>100</r:Total></r:Offer>
                </r:Root>
                """.formatted(ns);
        String actual = """
                <r:Root xmlns:r="%s">
                  <r:Offer OfferID="OF-1"><r:Total>100</r:Total></r:Offer>
                  <r:Offer OfferID="OF-2"><r:Total>200</r:Total></r:Offer>
                </r:Root>
                """.formatted(ns);
        var options = CompareOptions.defaults()
                .withElementMatches(ElementMatch.byAttribute(
                        NameRef.of(ns, "Offer"), NameRef.local("OfferID")));
        assertTrue(XmlComparer.compare(expected, actual, options).isEqual());
    }

    @Test
    void matchStillWorksWithIgnoreAttributes() {
        String expected = """
                <Root TimeStamp="old">
                  <Item id="2" flag="x">b</Item>
                  <Item id="1" flag="y">a</Item>
                </Root>
                """;
        String actual = """
                <Root TimeStamp="new">
                  <Item id="1" flag="y">a</Item>
                  <Item id="2" flag="x">b</Item>
                </Root>
                """;
        var options = CompareOptions.defaults()
                .withIgnoreAttributes(NameRef.local("TimeStamp"))
                .withElementMatches(ElementMatch.byAttribute("Item", "id"));
        assertTrue(XmlComparer.compare(expected, actual, options).isEqual());
    }
}
