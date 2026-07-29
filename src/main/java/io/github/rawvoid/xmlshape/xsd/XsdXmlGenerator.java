package io.github.rawvoid.xmlshape.xsd;

import io.github.rawvoid.xmlshape.xsd.internal.SchemaModelLoader;
import io.github.rawvoid.xmlshape.xsd.internal.XmlInstanceBuilder;
import org.apache.xerces.xs.XSElementDeclaration;
import org.apache.xerces.xs.XSModel;
import org.w3c.dom.Document;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Path;

/**
 * Generates XML instance documents from an XML Schema (XSD).
 *
 * <p>By default every element and attribute is emitted (including optional ones and every
 * {@code xs:choice} alternative), sample lexical values are used for simple content, and
 * namespaces follow the schema ({@code elementFormDefault} / {@code attributeFormDefault},
 * including chameleon includes). Abstract element particles expand via their
 * substitution group ({@link ChoiceStrategy#ALL} emits every concrete member;
 * {@link ChoiceStrategy#FIRST} emits the first); with no concrete members the particle
 * is skipped. {@code xs:any}/{@code anyAttribute} are skipped unless
 * {@link GenerateOptions#emitWildcardPlaceholders()} is enabled.
 *
 * <p>Output is a structural template expanded in full. Recursion is stopped only when the
 * same type already appears on the ancestor stack (that element is emitted as a shell with
 * attributes / simple content only). When {@link ChoiceStrategy#ALL} is used, the result
 * may not validate against the schema.
 */
public final class XsdXmlGenerator {
    private XsdXmlGenerator() {
    }

    public static String generate(Path schemaPath, String rootElementLocalName) {
        return generate(schemaPath, rootElementLocalName, null, GenerateOptions.defaults());
    }

    public static String generate(Path schemaPath, String rootElementLocalName, GenerateOptions options) {
        return generate(schemaPath, rootElementLocalName, null, options);
    }

    /**
     * @param rootNamespace target namespace of the root element, or {@code null} when the local name is unique
     */
    public static String generate(Path schemaPath, String rootElementLocalName, String rootNamespace,
                                  GenerateOptions options) {
        XSModel model = SchemaModelLoader.load(schemaPath);
        return generate(model, rootElementLocalName, rootNamespace, options);
    }

    public static String generate(URI schemaUri, String rootElementLocalName, GenerateOptions options) {
        return generate(schemaUri, rootElementLocalName, null, options);
    }

    /**
     * @param rootNamespace target namespace of the root element, or {@code null} when the local name is unique
     */
    public static String generate(URI schemaUri, String rootElementLocalName, String rootNamespace,
                                  GenerateOptions options) {
        XSModel model = SchemaModelLoader.load(schemaUri);
        return generate(model, rootElementLocalName, rootNamespace, options);
    }

    static String generate(XSModel model, String rootElementLocalName, String rootNamespace, GenerateOptions options) {
        if (options == null) {
            options = GenerateOptions.defaults();
        }
        XSElementDeclaration root = SchemaModelLoader.findGlobalElement(model, rootElementLocalName, rootNamespace);
        Document document = new XmlInstanceBuilder(model, options).build(root);
        return serialize(document);
    }

    private static String serialize(Document document) {
        try {
            var transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            var writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            return writer.toString();
        } catch (TransformerException e) {
            throw new XsdXmlGenerationException("Failed to serialize generated XML", e);
        }
    }
}
