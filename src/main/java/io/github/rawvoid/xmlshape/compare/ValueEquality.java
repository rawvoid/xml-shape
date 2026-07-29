package io.github.rawvoid.xmlshape.compare;

import java.util.Optional;

/**
 * Strategy for deciding whether two value strings are equal after whitespace handling.
 *
 * <p>Return {@link Optional#empty()} when this strategy does not apply; the comparer will try the
 * next strategy in a {@linkplain ValueEqualities#chain chain} or fall back to literal string
 * equality.
 */
@FunctionalInterface
public interface ValueEquality {
    /**
     * @return {@code Optional.of(true/false)} when this strategy decides equality;
     * {@code Optional.empty()} when it does not apply to these values
     */
    Optional<Boolean> equalTo(ValueContext context, String expected, String actual);
}
