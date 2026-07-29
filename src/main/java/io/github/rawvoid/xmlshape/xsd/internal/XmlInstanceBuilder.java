package io.github.rawvoid.xmlshape.xsd.internal;

import io.github.rawvoid.xmlshape.xsd.ChoiceStrategy;
import io.github.rawvoid.xmlshape.xsd.GenerateOptions;
import io.github.rawvoid.xmlshape.xsd.XsdXmlGenerationException;
import org.apache.xerces.xs.XSAttributeDeclaration;
import org.apache.xerces.xs.XSAttributeUse;
import org.apache.xerces.xs.XSComplexTypeDefinition;
import org.apache.xerces.xs.XSConstants;
import org.apache.xerces.xs.XSElementDeclaration;
import org.apache.xerces.xs.XSModel;
import org.apache.xerces.xs.XSModelGroup;
import org.apache.xerces.xs.XSObject;
import org.apache.xerces.xs.XSObjectList;
import org.apache.xerces.xs.XSParticle;
import org.apache.xerces.xs.XSSimpleTypeDefinition;
import org.apache.xerces.xs.XSTerm;
import org.apache.xerces.xs.XSTypeDefinition;
import org.apache.xerces.xs.XSValue;
import org.apache.xerces.xs.XSWildcard;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Walks Xerces schema components and builds a DOM instance document.
 *
 * <p>Templates expand fully. Recursion is stopped only when the same type already appears
 * on the ancestor type stack: that element is emitted as a shell (attributes and simple
 * content only, no child particles).
 */
public final class XmlInstanceBuilder {
    static final String WILDCARD_NS = "http://rawvoid.github.io/xml-shape/wildcard";
    static final String WILDCARD_ELEMENT = "any";
    static final String WILDCARD_ATTRIBUTE = "anyAttr";

    private final XSModel model;
    private final GenerateOptions options;
    private final SampleValueProvider samples = new SampleValueProvider();
    private final Document document;
    private final Deque<String> typeStack = new ArrayDeque<>();
    private final Map<String, String> attributePrefixes = new HashMap<>();
    private int attributePrefixCounter;

    public XmlInstanceBuilder(XSModel model, GenerateOptions options) {
        if (model == null) {
            throw new XsdXmlGenerationException("model must not be null");
        }
        this.model = model;
        this.options = options == null ? GenerateOptions.defaults() : options;
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            this.document = factory.newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw new XsdXmlGenerationException("Failed to create DOM document", e);
        }
    }

    public Document build(XSElementDeclaration rootElement) {
        if (rootElement == null) {
            throw new XsdXmlGenerationException("rootElement must not be null");
        }
        Element root = createElement(rootElement);
        document.appendChild(root);
        fillElement(rootElement, root);
        return document;
    }

    private void fillElement(XSElementDeclaration declaration, Element element) {
        String typeKey = typeKey(declaration.getTypeDefinition());
        if (typeStack.contains(typeKey)) {
            fillShell(declaration, element);
            return;
        }

        typeStack.addLast(typeKey);
        try {
            fillContent(declaration, element, true);
        } finally {
            typeStack.removeLast();
        }
    }

    /**
     * Recursive shell: attributes and simple content only — no child particles.
     */
    private void fillShell(XSElementDeclaration declaration, Element element) {
        fillContent(declaration, element, false);
    }

    private void fillContent(XSElementDeclaration declaration, Element element, boolean expandParticles) {
        applyValueConstraints(declaration, element);

        XSTypeDefinition type = declaration.getTypeDefinition();
        if (type == null) {
            return;
        }

        if (type.getTypeCategory() == XSTypeDefinition.SIMPLE_TYPE) {
            if (isBlank(element.getTextContent())) {
                element.setTextContent(samples.sample((XSSimpleTypeDefinition) type));
            }
            return;
        }

        var complexType = (XSComplexTypeDefinition) type;
        writeAttributes(complexType, element);
        writeAttributeWildcard(complexType, element);

        switch (complexType.getContentType()) {
            case XSComplexTypeDefinition.CONTENTTYPE_EMPTY -> {
                // nothing
            }
            case XSComplexTypeDefinition.CONTENTTYPE_SIMPLE -> {
                if (isBlank(element.getTextContent())) {
                    XSSimpleTypeDefinition simple = complexType.getSimpleType();
                    element.setTextContent(samples.sample(simple));
                }
            }
            case XSComplexTypeDefinition.CONTENTTYPE_ELEMENT,
                 XSComplexTypeDefinition.CONTENTTYPE_MIXED -> {
                if (expandParticles) {
                    XSParticle particle = complexType.getParticle();
                    if (particle != null) {
                        emitParticle(particle, element);
                    }
                }
            }
            default -> {
                // ignore unknown content kinds
            }
        }
    }

    private void applyValueConstraints(XSElementDeclaration declaration, Element element) {
        short constraint = declaration.getConstraintType();
        if (constraint == XSConstants.VC_FIXED || constraint == XSConstants.VC_DEFAULT) {
            String value = normalizedConstraintValue(declaration.getValueConstraintValue());
            if (value != null) {
                element.setTextContent(value);
            }
        }
    }

    private void writeAttributes(XSComplexTypeDefinition complexType, Element element) {
        XSObjectList uses = complexType.getAttributeUses();
        if (uses == null) {
            return;
        }
        for (int i = 0; i < uses.getLength(); i++) {
            XSObject item = uses.item(i);
            if (!(item instanceof XSAttributeUse use)) {
                continue;
            }
            if (!use.getRequired() && !options.includeOptionalAttributes()) {
                continue;
            }
            XSAttributeDeclaration attr = use.getAttrDeclaration();
            if (attr == null) {
                continue;
            }
            String value = attributeValue(use, attr);
            String attrNs = attr.getNamespace();
            String localName = attr.getName();
            if (attrNs == null || attrNs.isEmpty()) {
                element.setAttributeNS(null, localName, value);
            } else {
                String prefix = attributePrefixes.computeIfAbsent(attrNs, ns -> "ns" + (++attributePrefixCounter));
                element.setAttributeNS(attrNs, prefix + ":" + localName, value);
            }
        }
    }

    private String attributeValue(XSAttributeUse use, XSAttributeDeclaration attr) {
        short useConstraint = use.getConstraintType();
        if (useConstraint == XSConstants.VC_FIXED || useConstraint == XSConstants.VC_DEFAULT) {
            String value = normalizedConstraintValue(use.getValueConstraintValue());
            if (value != null) {
                return value;
            }
        }
        short declConstraint = attr.getConstraintType();
        if (declConstraint == XSConstants.VC_FIXED || declConstraint == XSConstants.VC_DEFAULT) {
            String value = normalizedConstraintValue(attr.getValueConstraintValue());
            if (value != null) {
                return value;
            }
        }
        return samples.sample(attr.getTypeDefinition());
    }

    private static String normalizedConstraintValue(XSValue constraintValue) {
        return constraintValue == null ? null : constraintValue.getNormalizedValue();
    }

    private void writeAttributeWildcard(XSComplexTypeDefinition complexType, Element element) {
        if (!options.emitWildcardPlaceholders()) {
            return;
        }
        XSWildcard wildcard = complexType.getAttributeWildcard();
        if (wildcard == null) {
            return;
        }
        element.setAttributeNS(WILDCARD_NS, "w:" + WILDCARD_ATTRIBUTE, "string");
    }

    private void emitParticle(XSParticle particle, Element parent) {
        int occurrences = occurrenceCount(particle);
        if (occurrences <= 0) {
            return;
        }
        for (int i = 0; i < occurrences; i++) {
            emitTerm(particle.getTerm(), parent);
        }
    }

    /**
     * First choice alternative in schema order that {@link #occurrenceCount} would emit.
     */
    private XSParticle firstViableParticle(XSObjectList particles) {
        for (int i = 0; i < particles.getLength(); i++) {
            if (particles.item(i) instanceof XSParticle particle && occurrenceCount(particle) > 0) {
                return particle;
            }
        }
        return null;
    }

    private int occurrenceCount(XSParticle particle) {
        int min = particle.getMinOccurs();
        if (min == 0 && !options.includeOptionalElements()) {
            return 0;
        }
        int count = Math.max(min, 1);
        if (particle.getMaxOccursUnbounded() || particle.getMaxOccurs() > 1) {
            count = Math.max(count, options.repeatingParticleCount());
            if (!particle.getMaxOccursUnbounded()) {
                count = Math.min(count, particle.getMaxOccurs());
            }
        } else if (particle.getMaxOccurs() == 0) {
            return 0;
        }
        return count;
    }

    private void emitTerm(XSTerm term, Element parent) {
        if (term == null) {
            return;
        }
        switch (term.getType()) {
            case XSConstants.ELEMENT_DECLARATION -> emitElementDeclaration((XSElementDeclaration) term, parent);
            case XSConstants.MODEL_GROUP -> emitModelGroup((XSModelGroup) term, parent);
            case XSConstants.WILDCARD -> emitElementWildcard(parent);
            default -> {
                // ignore
            }
        }
    }

    private void emitElementDeclaration(XSElementDeclaration declaration, Element parent) {
        if (declaration.getAbstract()) {
            List<XSElementDeclaration> members = resolveSubstitutionMembers(declaration);
            if (!members.isEmpty()) {
                for (XSElementDeclaration member : members) {
                    appendElement(member, parent);
                }
                return;
            }
            // No concrete substitutes: still emit the head by type for structural templates
            // (instance is not schema-valid for an abstract element name).
        }
        appendElement(declaration, parent);
    }

    private void appendElement(XSElementDeclaration declaration, Element parent) {
        Element child = createElement(declaration);
        parent.appendChild(child);
        fillElement(declaration, child);
    }

    /**
     * Concrete substitution-group members for an abstract head (never includes the abstract head itself).
     * {@link ChoiceStrategy#FIRST} keeps only the first member; {@link ChoiceStrategy#ALL} keeps all.
     */
    private List<XSElementDeclaration> resolveSubstitutionMembers(XSElementDeclaration head) {
        XSObjectList group = model.getSubstitutionGroup(head);
        List<XSElementDeclaration> members = new ArrayList<>();
        if (group != null) {
            for (int i = 0; i < group.getLength(); i++) {
                if (group.item(i) instanceof XSElementDeclaration member && !member.getAbstract()) {
                    members.add(member);
                    if (options.choiceStrategy() == ChoiceStrategy.FIRST) {
                        break;
                    }
                }
            }
        }
        return members;
    }

    private void emitModelGroup(XSModelGroup group, Element parent) {
        XSObjectList particles = group.getParticles();
        if (particles == null || particles.getLength() == 0) {
            return;
        }

        if (group.getCompositor() == XSModelGroup.COMPOSITOR_CHOICE
                && options.choiceStrategy() == ChoiceStrategy.FIRST) {
            XSParticle chosen = firstViableParticle(particles);
            if (chosen != null) {
                emitParticle(chosen, parent);
            }
            return;
        }

        // sequence, all, and choice with ALL strategy: expand every particle
        for (int i = 0; i < particles.getLength(); i++) {
            if (particles.item(i) instanceof XSParticle particle) {
                emitParticle(particle, parent);
            }
        }
    }

    private Element createElement(XSElementDeclaration declaration) {
        String localName = declaration.getName();
        String ns = declaration.getNamespace();
        if (ns == null || ns.isEmpty()) {
            return document.createElementNS(null, localName);
        }
        return document.createElementNS(ns, localName);
    }

    private void emitElementWildcard(Element parent) {
        if (!options.emitWildcardPlaceholders()) {
            return;
        }
        Element placeholder = document.createElementNS(WILDCARD_NS, WILDCARD_ELEMENT);
        placeholder.setTextContent("string");
        parent.appendChild(placeholder);
    }

    /**
     * Recursion key based on type identity so differently named elements of the same type
     * truncate together, while same local names with distinct types may still nest.
     */
    private static String typeKey(XSTypeDefinition type) {
        if (type == null) {
            return "null-type";
        }
        if (type.getAnonymous()) {
            return "anon@" + System.identityHashCode(type);
        }
        String ns = type.getNamespace();
        String name = type.getName();
        return (ns == null ? "" : ns) + "}" + (name == null ? "?" : name);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
