package io.github.rawvoid.xmlshape.compare;

/**
 * A single mismatch between expected and actual XML.
 *
 * @param path     location using {@code /Name[n]/...} (1-based same-name sibling index) and
 *                 {@code @attr} / {@code @{ns}attr} for attributes
 * @param type     mismatch kind
 * @param expected description of the expected value, or {@code null} when not applicable
 * @param actual   description of the actual value, or {@code null} when not applicable
 * @param message  human-readable summary
 */
public record Difference(
        String path,
        DifferenceType type,
        String expected,
        String actual,
        String message
) {
}
