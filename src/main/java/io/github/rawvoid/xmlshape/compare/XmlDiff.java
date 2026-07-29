package io.github.rawvoid.xmlshape.compare;

import java.util.List;
import java.util.Objects;

/**
 * Result of comparing two XML documents.
 */
public final class XmlDiff {
    private final List<Difference> differences;

    XmlDiff(List<Difference> differences) {
        this.differences = List.copyOf(Objects.requireNonNull(differences, "differences"));
    }

    public boolean isEqual() {
        return differences.isEmpty();
    }

    public List<Difference> differences() {
        return differences;
    }

    /**
     * Multi-line summary of all differences, suitable for JUnit message suppliers
     * (e.g. {@code assertTrue(diff.isEqual(), diff::failureMessage)}).
     */
    public String failureMessage() {
        var sb = new StringBuilder();
        sb.append("XML comparison failed (").append(differences.size()).append(" difference(s)):");
        for (var d : differences) {
            sb.append("\n  ").append(d.path()).append(": ").append(d.message());
        }
        return sb.toString();
    }
}
