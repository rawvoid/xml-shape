package io.github.rawvoid.xmlshape.xsd.internal;

import io.github.rawvoid.xmlshape.xsd.XsdXmlGenerationException;
import org.apache.xerces.impl.xs.XMLSchemaLoader;
import org.apache.xerces.xni.XMLResourceIdentifier;
import org.apache.xerces.xni.XNIException;
import org.apache.xerces.xni.grammars.XMLGrammarDescription;
import org.apache.xerces.xni.parser.XMLEntityResolver;
import org.apache.xerces.xni.parser.XMLErrorHandler;
import org.apache.xerces.xni.parser.XMLInputSource;
import org.apache.xerces.xni.parser.XMLParseException;
import org.apache.xerces.xs.XSConstants;
import org.apache.xerces.xs.XSElementDeclaration;
import org.apache.xerces.xs.XSModel;
import org.apache.xerces.xs.XSNamedMap;
import org.apache.xerces.xs.XSObject;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads an XSD into a Xerces {@link XSModel} and resolves global element declarations.
 *
 * <p>External DTDs referenced by schema documents (e.g. W3C {@code XMLSchema.dtd} on
 * official xmldsig) are not fetched: the component model comes from the XSD body, and
 * network-dependent DTD resolution makes offline / flaky networks fail intermittently.
 */
public final class SchemaModelLoader {
    private SchemaModelLoader() {
    }

    public static XSModel load(URI schemaUri) {
        if (schemaUri == null) {
            throw new XsdXmlGenerationException("schemaUri must not be null");
        }
        try {
            var loader = new XMLSchemaLoader();
            var errors = new CollectingErrorHandler();
            loader.setErrorHandler(errors);
            loader.setEntityResolver(SuppressExternalDtdResolver.INSTANCE);
            var model = loader.loadURI(schemaUri.toString());
            errors.throwIfFailed("Failed to load schema: " + schemaUri);
            if (model == null) {
                throw new XsdXmlGenerationException("Failed to load schema: " + schemaUri);
            }
            return model;
        } catch (XsdXmlGenerationException e) {
            throw e;
        } catch (Exception e) {
            throw new XsdXmlGenerationException("Failed to load schema: " + schemaUri, e);
        }
    }

    public static XSModel load(Path schemaPath) {
        if (schemaPath == null) {
            throw new XsdXmlGenerationException("schemaPath must not be null");
        }
        var absolute = schemaPath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absolute)) {
            throw new XsdXmlGenerationException("Schema file does not exist: " + absolute);
        }
        return load(absolute.toUri());
    }

    /**
     * Finds a global element by local name.
     *
     * @param preferredNamespace target namespace of the root, or {@code null} to accept a unique match
     * @throws XsdXmlGenerationException if the name is missing, the preferred namespace has no match,
     *                                   or {@code preferredNamespace} is null and multiple namespaces define the name
     */
    public static XSElementDeclaration findGlobalElement(XSModel model, String localName, String preferredNamespace) {
        if (model == null) {
            throw new XsdXmlGenerationException("model must not be null");
        }
        if (localName == null || localName.isBlank()) {
            throw new XsdXmlGenerationException("root element name must not be blank");
        }

        if (preferredNamespace != null) {
            var preferred = model.getElementDeclaration(localName, preferredNamespace);
            if (preferred != null) {
                return preferred;
            }
            throw new XsdXmlGenerationException(
                    "Global element not found: {" + preferredNamespace + "}" + localName);
        }

        List<XSElementDeclaration> matches = new ArrayList<>();
        XSNamedMap elements = model.getComponents(XSConstants.ELEMENT_DECLARATION);
        for (int i = 0; i < elements.getLength(); i++) {
            XSObject item = elements.item(i);
            if (item instanceof XSElementDeclaration element && localName.equals(element.getName())) {
                matches.add(element);
            }
        }

        if (matches.isEmpty()) {
            var noNs = model.getElementDeclaration(localName, null);
            if (noNs != null) {
                return noNs;
            }
            throw new XsdXmlGenerationException("Global element not found: " + localName);
        }
        if (matches.size() == 1) {
            return matches.getFirst();
        }

        var namespaces = matches.stream()
                .map(el -> el.getNamespace() == null ? "(no namespace)" : el.getNamespace())
                .distinct()
                .sorted()
                .toList();
        throw new XsdXmlGenerationException(
                "Ambiguous global element '" + localName + "' in namespaces " + namespaces
                        + "; pass rootNamespace to disambiguate");
    }

    /**
     * Returns an empty input for external DTD subset resolution so Xerces does not
     * contact the network. Schema {@code import}/{@code include} still use default resolution.
     */
    private static final class SuppressExternalDtdResolver implements XMLEntityResolver {
        static final SuppressExternalDtdResolver INSTANCE = new SuppressExternalDtdResolver();

        @Override
        public XMLInputSource resolveEntity(XMLResourceIdentifier resourceIdentifier)
                throws XNIException, IOException {
            if (resourceIdentifier instanceof XMLGrammarDescription description
                    && XMLGrammarDescription.XML_DTD.equals(description.getGrammarType())) {
                return new XMLInputSource(
                        resourceIdentifier.getPublicId(),
                        resourceIdentifier.getLiteralSystemId(),
                        resourceIdentifier.getBaseSystemId(),
                        new StringReader(""),
                        null);
            }
            return null;
        }
    }

    /**
     * Collects Xerces schema errors and fails the load with location context.
     */
    private static final class CollectingErrorHandler implements XMLErrorHandler {
        private final List<String> problems = new ArrayList<>();

        @Override
        public void warning(String domain, String key, XMLParseException exception) {
            // warnings are ignored for load success
        }

        @Override
        public void error(String domain, String key, XMLParseException exception) {
            problems.add(format("error", exception));
        }

        @Override
        public void fatalError(String domain, String key, XMLParseException exception) {
            problems.add(format("fatal", exception));
        }

        void throwIfFailed(String prefix) {
            if (!problems.isEmpty()) {
                throw new XsdXmlGenerationException(prefix + " — " + String.join("; ", problems));
            }
        }

        private static String format(String severity, XMLParseException exception) {
            String systemId = exception.getExpandedSystemId() != null
                    ? exception.getExpandedSystemId()
                    : exception.getLiteralSystemId();
            return severity + " at " + systemId
                    + ":" + exception.getLineNumber()
                    + ":" + exception.getColumnNumber()
                    + " " + exception.getMessage();
        }
    }
}
