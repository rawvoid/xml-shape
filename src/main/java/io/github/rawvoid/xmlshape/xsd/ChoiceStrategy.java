package io.github.rawvoid.xmlshape.xsd;

/**
 * Strategy for expanding multi-branch schema constructs in structural templates.
 *
 * <p>Applies to:
 * <ul>
 *   <li>{@code xs:choice} model groups</li>
 *   <li>abstract element particles resolved via their substitution group
 *       (concrete members only; the abstract head is never emitted, and particles with
 *       no concrete members are skipped)</li>
 * </ul>
 */
public enum ChoiceStrategy {
    /**
     * Emit every alternative (every choice branch, or every concrete substitution-group
     * member). Prefer for complete structure templates; the instance may not validate
     * against the schema.
     */
    ALL,
    /**
     * Emit a single alternative.
     *
     * <p>For {@code xs:choice}: the first branch in schema document order that
     * {@link GenerateOptions} would actually emit (for example, skip a leading
     * {@code minOccurs=0} branch when optional elements are excluded).
     *
     * <p>For substitution groups: the first concrete member in schema order.
     */
    FIRST
}
