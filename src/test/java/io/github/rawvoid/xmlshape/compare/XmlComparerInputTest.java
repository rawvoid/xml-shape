package io.github.rawvoid.xmlshape.compare;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XmlComparerInputTest {

    private static final String XML = """
            <Root xmlns="http://example.com">
              <Item id="1">alpha</Item>
            </Root>
            """;

    @TempDir
    Path tempDir;

    @Test
    void comparePaths() throws Exception {
        Path a = tempDir.resolve("a.xml");
        Path b = tempDir.resolve("b.xml");
        Files.writeString(a, XML);
        Files.writeString(b, XML);
        assertTrue(XmlComparer.compare(a, b).isEqual());
    }

    @Test
    void compareNodesFromParsedDocuments() throws Exception {
        Document expected = parse(XML);
        Document actual = parse(XML);
        assertTrue(XmlComparer.compare(expected, actual).isEqual());
        assertTrue(XmlComparer.compare(expected.getDocumentElement(), actual.getDocumentElement()).isEqual());
    }

    @Test
    void compareInputStreamsAndReaders() {
        var expIn = new ByteArrayInputStream(XML.getBytes(StandardCharsets.UTF_8));
        var actIn = new ByteArrayInputStream(XML.getBytes(StandardCharsets.UTF_8));
        assertTrue(XmlComparer.compare(expIn, actIn).isEqual());

        assertTrue(XmlComparer.compare(new StringReader(XML), new StringReader(XML)).isEqual());
    }

    @Test
    void nullInputsThrow() {
        assertThrows(XmlCompareException.class, () -> XmlComparer.compare((String) null, XML));
        assertThrows(XmlCompareException.class, () -> XmlComparer.compare((Path) null, tempDir.resolve("x.xml")));
        assertThrows(XmlCompareException.class, () -> XmlComparer.compare((Node) null, parse(XML)));
    }

    @Test
    void nonElementNodeThrows() throws Exception {
        Document doc = parse(XML);
        Node text = doc.createTextNode("x");
        assertThrows(XmlCompareException.class, () -> XmlComparer.compare(text, doc));
    }

    @Test
    void invalidPathContentThrows() throws Exception {
        Path bad = tempDir.resolve("bad.xml");
        Files.writeString(bad, "not-xml");
        Path good = tempDir.resolve("good.xml");
        Files.writeString(good, XML);
        assertThrows(XmlCompareException.class, () -> XmlComparer.compare(bad, good));
    }

    private static Document parse(String xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
}
