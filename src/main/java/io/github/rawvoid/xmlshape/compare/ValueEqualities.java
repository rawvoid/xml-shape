package io.github.rawvoid.xmlshape.compare;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Built-in {@link ValueEquality} strategies. Defaults leave comparison literal; call sites opt in.
 *
 * <p>Typed strategies only decide when <em>both</em> sides parse as that type (same temporal kind
 * for date-time), otherwise they return empty so another strategy or literal equality can apply.
 */
public final class ValueEqualities {
    private ValueEqualities() {
    }

    /**
     * Tries strategies in order; the first non-empty result wins.
     */
    public static ValueEquality chain(ValueEquality... strategies) {
        Objects.requireNonNull(strategies, "strategies");
        var copy = Arrays.copyOf(strategies, strategies.length);
        for (var s : copy) {
            Objects.requireNonNull(s, "strategy");
        }
        return (ctx, expected, actual) -> {
            for (var strategy : copy) {
                var result = strategy.equalTo(ctx, expected, actual);
                if (result.isPresent()) {
                    return result;
                }
            }
            return Optional.empty();
        };
    }

    /**
     * Both sides parse as {@link BigDecimal} → numeric equality; otherwise empty.
     */
    public static ValueEquality numeric() {
        return (ctx, expected, actual) -> {
            var left = tryBigDecimal(expected);
            var right = tryBigDecimal(actual);
            if (left.isEmpty() || right.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(left.get().compareTo(right.get()) == 0);
        };
    }

    /**
     * Temporal equality with ISO-8601 parsers and zone-aware instants
     * ({@code ignoreTimeZone=false}). Equivalent to {@link #dateTime(boolean) dateTime(false)}.
     *
     * @see #dateTime(boolean)
     */
    public static ValueEquality dateTime() {
        return dateTime(false);
    }

    /**
     * Both sides parse as the same temporal kind (ISO-8601) → equal when values match.
     *
     * <p>Parsing uses ISO formatters in order: {@link DateTimeFormatter#ISO_DATE_TIME}
     * ({@link OffsetDateTime} or {@link LocalDateTime}), {@link DateTimeFormatter#ISO_DATE},
     * {@link DateTimeFormatter#ISO_TIME} ({@link OffsetTime} or {@link LocalTime}).
     *
     * <ul>
     *   <li>{@code ignoreTimeZone=false} (default):
     *     <ul>
     *       <li>{@link OffsetDateTime} → compared as {@link Instant}</li>
     *       <li>{@link OffsetTime} → {@link OffsetTime#equals}</li>
     *       <li>both {@link LocalDateTime} / {@link LocalDate} / {@link LocalTime} → kind equals</li>
     *     </ul>
     *   </li>
     *   <li>{@code ignoreTimeZone=true}:
     *     <ul>
     *       <li>zoned date-times ({@link OffsetDateTime}) → local date-time fields only</li>
     *       <li>zoned times ({@link OffsetTime}) → local time fields only</li>
     *       <li>then same-kind equals as above</li>
     *     </ul>
     *   </li>
     *   <li>mixed kinds (e.g. date vs date-time, time vs date-time) → empty (no decision)</li>
     * </ul>
     *
     * @param ignoreTimeZone when {@code true}, discard offsets and compare wall-clock fields
     */
    public static ValueEquality dateTime(boolean ignoreTimeZone) {
        return (ctx, expected, actual) -> {
            var left = tryTemporal(expected, ignoreTimeZone);
            var right = tryTemporal(actual, ignoreTimeZone);
            if (left.isEmpty() || right.isEmpty()) {
                return Optional.empty();
            }
            return left.get().equalTo(right.get());
        };
    }

    /**
     * Both sides parse as ISO-8601 {@link Duration} (e.g. {@code P1D}, {@code PT24H}) → equal when
     * the durations match; otherwise empty.
     *
     * <p>Uses {@link Duration#parse}; day-based and time-based forms that resolve to the same
     * length are equal ({@code P1D} ≡ {@code PT24H}). Year/month-based forms are not supported by
     * {@link Duration} and fall through to literal equality.
     */
    public static ValueEquality duration() {
        return (ctx, expected, actual) -> {
            var left = tryDuration(expected);
            var right = tryDuration(actual);
            if (left.isEmpty() || right.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(left.get().equals(right.get()));
        };
    }

    /**
     * Both sides look like booleans ({@code true}/{@code false}/{@code 1}/{@code 0}, case-insensitive)
     * → semantic equality; otherwise empty.
     */
    public static ValueEquality bool() {
        return (ctx, expected, actual) -> {
            var left = tryBoolean(expected);
            var right = tryBoolean(actual);
            if (left.isEmpty() || right.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(left.get().equals(right.get()));
        };
    }

    /**
     * Applies {@code inner} only when {@link ValueContext#localName()} is one of {@code localNames}.
     */
    public static ValueEquality forLocalNames(ValueEquality inner, String... localNames) {
        Objects.requireNonNull(inner, "inner");
        Objects.requireNonNull(localNames, "localNames");
        Set<String> names = Arrays.stream(localNames)
                .peek(n -> {
                    if (n == null || n.isEmpty()) {
                        throw new IllegalArgumentException("localNames must not contain null/empty");
                    }
                })
                .collect(Collectors.toUnmodifiableSet());
        if (names.isEmpty()) {
            throw new IllegalArgumentException("localNames must not be empty");
        }
        return (ctx, expected, actual) -> {
            if (!names.contains(ctx.localName())) {
                return Optional.empty();
            }
            return inner.equalTo(ctx, expected, actual);
        };
    }

    /**
     * Common typed chain: date-time, duration, numeric, then boolean. Not applied by
     * {@link CompareOptions#defaults()}; opt in explicitly.
     */
    public static ValueEquality commonTypes() {
        return chain(dateTime(), duration(), numeric(), bool());
    }

    private static Optional<BigDecimal> tryBigDecimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(raw.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Optional<Boolean> tryBoolean(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "true", "1" -> Optional.of(true);
            case "false", "0" -> Optional.of(false);
            default -> Optional.empty();
        };
    }

    private static Optional<Duration> tryDuration(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Duration.parse(raw.trim()));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /**
     * ISO-8601 lexical forms via {@link DateTimeFormatter#ISO_DATE_TIME},
     * {@link DateTimeFormatter#ISO_DATE}, and {@link DateTimeFormatter#ISO_TIME}.
     * Offset-optional forms use {@link DateTimeFormatter#parseBest} so plain local
     * date-times/times still resolve when no zone is present.
     */
    private static Optional<TemporalValue> tryTemporal(String raw, boolean ignoreTimeZone) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String s = raw.trim();

        try {
            TemporalAccessor best = DateTimeFormatter.ISO_DATE_TIME.parseBest(
                    s, OffsetDateTime::from, LocalDateTime::from);
            if (best instanceof OffsetDateTime odt) {
                if (ignoreTimeZone) {
                    return Optional.of(new TemporalValue.LocalDateTimeValue(odt.toLocalDateTime()));
                }
                return Optional.of(new TemporalValue.InstantValue(odt.toInstant()));
            }
            return Optional.of(new TemporalValue.LocalDateTimeValue((LocalDateTime) best));
        } catch (DateTimeParseException ignored) {
            // try next
        }
        try {
            return Optional.of(new TemporalValue.LocalDateValue(
                    LocalDate.parse(s, DateTimeFormatter.ISO_DATE)));
        } catch (DateTimeParseException ignored) {
            // try next
        }
        try {
            TemporalAccessor best = DateTimeFormatter.ISO_TIME.parseBest(
                    s, OffsetTime::from, LocalTime::from);
            if (best instanceof OffsetTime ot) {
                if (ignoreTimeZone) {
                    return Optional.of(new TemporalValue.LocalTimeValue(ot.toLocalTime()));
                }
                return Optional.of(new TemporalValue.OffsetTimeValue(ot));
            }
            return Optional.of(new TemporalValue.LocalTimeValue((LocalTime) best));
        } catch (DateTimeParseException ignored) {
            return Optional.empty();
        }
    }

    private sealed interface TemporalValue {
        Optional<Boolean> equalTo(TemporalValue other);

        record InstantValue(Instant instant) implements TemporalValue {
            @Override
            public Optional<Boolean> equalTo(TemporalValue other) {
                if (other instanceof InstantValue(Instant o)) {
                    return Optional.of(instant.equals(o));
                }
                return Optional.empty();
            }
        }

        record LocalDateTimeValue(LocalDateTime value) implements TemporalValue {
            @Override
            public Optional<Boolean> equalTo(TemporalValue other) {
                if (other instanceof LocalDateTimeValue(LocalDateTime o)) {
                    return Optional.of(value.equals(o));
                }
                return Optional.empty();
            }
        }

        record LocalDateValue(LocalDate value) implements TemporalValue {
            @Override
            public Optional<Boolean> equalTo(TemporalValue other) {
                if (other instanceof LocalDateValue(LocalDate o)) {
                    return Optional.of(value.equals(o));
                }
                return Optional.empty();
            }
        }

        record LocalTimeValue(LocalTime value) implements TemporalValue {
            @Override
            public Optional<Boolean> equalTo(TemporalValue other) {
                if (other instanceof LocalTimeValue(LocalTime o)) {
                    return Optional.of(value.equals(o));
                }
                return Optional.empty();
            }
        }

        record OffsetTimeValue(OffsetTime value) implements TemporalValue {
            @Override
            public Optional<Boolean> equalTo(TemporalValue other) {
                if (other instanceof OffsetTimeValue(OffsetTime o)) {
                    return Optional.of(value.equals(o));
                }
                return Optional.empty();
            }
        }
    }
}
