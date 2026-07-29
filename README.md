# xml-shape

Java library for generating XML **structure templates** from XSD, and for structural comparison of two XML documents (elements / namespaces / attributes / text).

- **Generation**: `XsdXmlGenerator` reads an XML Schema and emits a mostly complete instance document (element/attribute skeleton + sample values for simple types). Expansion follows a **template** semantics; the result is **not guaranteed** to validate against the Schema.
- **Comparison**: `XmlComparer` compares two XML strings by DOM structure and returns an `XmlDiff`. In tests, assert with JUnit 5 (prefix differences are ignored; whitespace and child order can optionally be ignored).

## Requirements

- Java **21+**
- Maven 3.x
- Runtime dependency on [Xerces](https://xerces.apache.org/) (`xercesImpl`, see `pom.xml`)

## Quick start

```java
import io.github.rawvoid.xmlshape.xsd.ChoiceStrategy;
import io.github.rawvoid.xmlshape.xsd.GenerateOptions;
import io.github.rawvoid.xmlshape.xsd.XsdXmlGenerator;

import java.nio.file.Path;

Path schema = Path.of("path/to/schema.xsd");

// Defaults: include optional elements/attributes; expand all xs:choice branches
String xml = XsdXmlGenerator.generate(schema, "Root");

// Custom options
var options = GenerateOptions.defaults()
  .withChoiceStrategy(ChoiceStrategy.FIRST)
  .withIncludeOptionalElements(false)
  .withIncludeOptionalAttributes(false)
  .withRepeatingParticleCount(2)
  .withEmitWildcardPlaceholders(true);
String focused = XsdXmlGenerator.generate(schema, "Root", options);

// When the same global element name exists in multiple namespaces, pass the target namespace to disambiguate
String namespaced = XsdXmlGenerator.generate(
  schema, "Payload", "http://example.com/a", GenerateOptions.defaults());
```

Loading a Schema from a `URI` is also supported (`include`/`import` relative paths resolve against that URI).

## GenerateOptions

| Option | Default | Meaning |
|--------|---------|---------|
| `choiceStrategy` | `ALL` | How to expand `xs:choice` and **abstract substitution groups**: `ALL` emits every alternative; `FIRST` takes the first branch (or first concrete substitution member) that would actually be generated, in schema order |
| `includeOptionalElements` | `true` | Whether to emit particles with `minOccurs=0` |
| `includeOptionalAttributes` | `true` | Whether to emit non-required attributes |
| `repeatingParticleCount` | `1` | How many times to repeat when `maxOccurs > 1` (or unbounded); never exceeds `maxOccurs` |
| `emitWildcardPlaceholders` | `false` | Whether to emit placeholders for `xs:any` / `anyAttribute` |

## Behavioral notes

- **Full template**: Expand as much structure as the options allow.
- **Type-cycle truncation**: When the same type already appears on the ancestor stack, that element is emitted as a shell only (attributes and simple content; no child particles), to avoid infinite recursion.
- **Namespaces**: Follow the Schema’s `elementFormDefault` / `attributeFormDefault` and chameleon-include resolution.
- **Sample values**: Built-in types and common facets (enumeration, length, numeric bounds, `totalDigits` / `fractionDigits`, etc.); values are **not** synthesized from arbitrary `pattern`s. `ID` / `IDREF` are bound to each other when possible.
- **`ChoiceStrategy.ALL`**: Useful for surveying every branch; the instance typically will not pass schema validation.

Load failures or a missing root element throw `XsdXmlGenerationException`.

## XML comparison

```java
import io.github.rawvoid.xmlshape.compare.CompareOptions;
import io.github.rawvoid.xmlshape.compare.ElementMatch;
import io.github.rawvoid.xmlshape.compare.NameRef;
import io.github.rawvoid.xmlshape.compare.ValueEqualities;
import io.github.rawvoid.xmlshape.compare.XmlComparer;

import static org.junit.jupiter.api.Assertions.assertTrue;

// Compare + JUnit 5 assertion (use failureMessage so path and diffs print on failure)
var diff = XmlComparer.compare(expectedXml, actualXml);
assertTrue(diff.isEqual(), diff::failureMessage);

// Path / Node(Document|Element) / InputStream / Reader are also supported (symmetric types on both sides)
// XmlComparer.compare(expectedPath, actualPath);
// XmlComparer.compare(expectedNode, actualNode);

// With a business context message
assertTrue(diff.isEqual(),
    () -> "Response structure mismatch\n" + diff.failureMessage());

// Options: keep literal whitespace; ignore child element order
var options = CompareOptions.defaults()
  .withIgnoreWhitespace(false)
  .withOrderSensitive(false);
var ordered = XmlComparer.compare(expectedXml, actualXml, options);
assertTrue(ordered.isEqual(), ordered::failureMessage);

// Structure only (element tree + attribute names); do not compare text or attribute values
var structure = XmlComparer.compare(expectedXml, actualXml, CompareOptions.structureOnly());
assertTrue(structure.isEqual(), structure::failureMessage);

// Typed value equality (default remains literal; typed comparison applies only when both sides parse)
var typed = CompareOptions.defaults()
  .withValueEquality(ValueEqualities.chain(
    ValueEqualities.forLocalNames(ValueEqualities.dateTime(), "TimeStamp"),
    ValueEqualities.forLocalNames(ValueEqualities.numeric(), "Amount")));

// Or enable common types in one shot: ValueEqualities.commonTypes()
assertTrue(XmlComparer.compare(expectedXml, actualXml, typed).isEqual(), () -> "…");

// Ignore dynamic attributes / whole elements; optionally compare only a subtree
var focused = CompareOptions.defaults()
  .withIgnoreAttributes(NameRef.local("TimeStamp"), NameRef.local("EchoToken"))
  .withIgnoreElements(NameRef.of("http://example.com", "Metadata"))
  .withRootPath("/Root[1]/Body[1]");
var focusedDiff = XmlComparer.compare(expectedXml, actualXml, focused);
assertTrue(focusedDiff.isEqual(), focusedDiff::failureMessage);

// Align same-name lists by business key (order-independent; takes precedence over orderSensitive)
var matched = CompareOptions.defaults()
  .withElementMatches(
    ElementMatch.byAttribute("Item", "id"),
    ElementMatch.byChildText("Passenger", "PaxID"));
var matchedDiff = XmlComparer.compare(expectedXml, actualXml, matched);
assertTrue(matchedDiff.isEqual(), matchedDiff::failureMessage);

// Process differences one by one
diff.differences().forEach(d -> System.out.println(d.path() + ": " + d.message()));
```

### CompareOptions

| Option | Default | Meaning |
|--------|---------|---------|
| `ignoreWhitespace` | `true` | Trim text and collapse consecutive whitespace; pure-whitespace text nodes are ignored (only meaningful when `compareValues=true`) |
| `orderSensitive` | `true` | Whether child nodes must align in document order; when `false`, elements are grouped by QName (same-name siblings still pair in appearance order) and sibling text is compared as a multiset |
| `compareValues` | `true` | Whether to compare text and attribute values; when `false`, only the element tree and attribute presence are compared (use `CompareOptions.structureOnly()`) |
| `valueEquality` | `null` | Optional `ValueEquality`; applied after whitespace handling. Built-ins in `ValueEqualities` (`numeric` / `dateTime` / `bool` / `commonTypes` / `forLocalNames` / `chain`). Off by default so order numbers and similar values are not mis-parsed as numbers |
| `ignoredAttributes` | empty | Attribute QNames excluded from comparison (`NameRef`; presence and value both ignored) |
| `ignoredElements` | empty | Element QNames whose entire subtrees are dropped (including descendants) |
| `rootPath` | `null` | Start comparison at this subtree path on both sides, e.g. `/Root[1]/Body[1]` or `/{ns}Root[1]/...`; missing path throws `XmlCompareException` |
| `elementMatches` | empty | Pair same-name siblings by key: `ElementMatch.byAttribute` / `byChildText`; matching QNames take **precedence** over document order |

### Comparison conventions

- **Identity**: Elements and attributes use `(namespace URI, local name)`; prefix strings are not compared.
- **Attributes**: Compared as a QName set (order-independent); `xmlns` / `xmlns:*` declarations themselves are ignored. When `compareValues=false`, attribute presence is still checked, not values. Attributes listed in `ignoredAttributes` are fully excluded.
- **Text**: Only direct child text/CDATA is considered; text inside descendant elements is not concatenated. When `compareValues=false`, text is ignored entirely.
- **Value equality**: Literal equality by default; with `valueEquality` configured, typed equality applies only when the strategy reports a match, otherwise falls back to literal. `dateTime` equals only when both sides parse to the same temporal abstraction (Instant/Offset may interoperate; LocalDate and Instant do not).
- **Ignored**: Comments, processing instructions, and subtrees named in `ignoredElements`.
- **List key alignment**: With `elementMatches`, matching elements pair by attribute or child-element text key; if a key cannot be extracted, same-QName order pairing is used. When matches are configured, text and elements are no longer mixed positionally (elements pair by rule; text is compared separately).
- **Malformed XML / invalid rootPath**: throws `XmlCompareException`.

## Build and test

```bash
mvn test
```

Test resources live under `src/test/resources/xsd/` and `src/test/resources/schemas/` (focused unit fixtures and an NDC `AirShoppingRS` integration sample).

GitHub Actions (`.github/workflows/ci.yml`) runs the same tests on pull requests and on pushes to `main` / version tags.

## Publishing

Maven coordinates: `io.github.rawvoid:xml-shape`.

| Trigger | Condition | Destination |
|---------|-----------|-------------|
| Push to `main` | `pom` version ends with `-SNAPSHOT` | [Central Portal SNAPSHOT](https://central.sonatype.com/repository/maven-snapshots/) |
| Push release tag | `pom` version equals the tag without `v`, and is not a SNAPSHOT | Maven Central (release) + GitHub Release |

**Release tags** (all publish to Maven Central as formal artifacts, not the SNAPSHOT repo):

| Tag | Matching `pom` version |
|-----|------------------------|
| `v1.0.0` | `1.0.0` |
| `v1.0.0-alpha` / `v1.0.0-alpha.1` / `v1.0.0-alpha-1` | same without leading `v` |
| `v1.0.0-beta` / `v1.0.0-beta.1` | same without leading `v` |
| `v1.0.0-rc` / `v1.0.0-rc.1` | same without leading `v` |

Qualifier must be lowercase `alpha`, `beta`, or `rc`, with an optional numeric suffix (`.N`, `-N`, or `N`).

Release flow (manual version bump, then tag):

1. Set `<version>` in `pom.xml` to the release version (e.g. `1.0.0` or `1.0.0-rc.1`), merge to `main`.
2. Tag and push with the same version prefixed by `v`: `git tag v1.0.0-rc.1 && git push origin v1.0.0-rc.1`.
3. After the release workflow succeeds, bump `pom.xml` to the next development version (e.g. `1.0.0-SNAPSHOT` or `1.0.1-SNAPSHOT`) and merge.

Required repository secrets (same names as `jaxb-plugins`):

| Secret | Purpose |
|--------|---------|
| `MAVEN_CENTRAL_USERNAME` | Sonatype Central Portal token username |
| `MAVEN_CENTRAL_PASSWORD` | Sonatype Central Portal token password |
| `MAVEN_GPG_PRIVATE_KEY` | ASCII-armored GPG private key used for release signing |
| `MAVEN_GPG_PASSPHRASE` | Passphrase for that key |

Portal prerequisites: claim/verify the `io.github.rawvoid` namespace, generate a user token, and enable SNAPSHOTs for that namespace. Local publish (optional): `mvn -Ppublish deploy`.

## Module layout

```text
io.github.rawvoid.xmlshape.xsd
├── XsdXmlGenerator          # XSD → XML template
├── GenerateOptions
├── ChoiceStrategy
├── XsdXmlGenerationException
└── internal/
    ├── SchemaModelLoader
    ├── XmlInstanceBuilder
    └── SampleValueProvider

io.github.rawvoid.xmlshape.compare
├── XmlComparer              # Comparison entry point
├── CompareOptions
├── ValueEquality / ValueEqualities / ValueContext / ValueKind
├── NameRef                  # QName reference for ignore rules
├── ElementMatch             # List alignment by key
├── XmlDiff                  # isEqual / differences / failureMessage
├── Difference / DifferenceType
├── XmlCompareException
└── internal/
    ├── XmlParsers
    ├── PathResolver
    └── StructuralComparator
```
