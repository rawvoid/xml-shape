package io.github.rawvoid.xmlshape.xsd.internal;

import org.apache.xerces.xs.StringList;
import org.apache.xerces.xs.XSConstants;
import org.apache.xerces.xs.XSObjectList;
import org.apache.xerces.xs.XSSimpleTypeDefinition;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Produces lexical sample values for simple types (built-ins, enums, unions, lists).
 *
 * <p>Honors enumerations and common constraining facets ({@code length}/{@code minLength}/
 * {@code maxLength}, numeric bounds, {@code totalDigits}/{@code fractionDigits}).
 * Does <strong>not</strong> synthesize values from arbitrary {@code pattern} facets.
 * Generated {@code ID} values are recorded so later {@code IDREF} samples can bind to them.
 */
public final class SampleValueProvider {
    private int idCounter;
    private final List<String> generatedIds = new ArrayList<>();
    /** When IDREF is sampled before any ID, reserve that id for the next {@link #nextId()}. */
    private String pendingIdAssignment;

    public String nextId() {
        if (pendingIdAssignment != null) {
            String id = pendingIdAssignment;
            pendingIdAssignment = null;
            return id;
        }
        idCounter++;
        String id = "id" + idCounter;
        generatedIds.add(id);
        return id;
    }

    public String sample(XSSimpleTypeDefinition type) {
        if (type == null) {
            return "string";
        }

        StringList enums = type.getLexicalEnumeration();
        if (enums != null && enums.getLength() > 0) {
            return enums.item(0);
        }

        return switch (type.getVariety()) {
            case XSSimpleTypeDefinition.VARIETY_LIST -> sampleList(type);
            case XSSimpleTypeDefinition.VARIETY_UNION -> sampleUnion(type);
            default -> applyFacets(type, sampleAtomic(type));
        };
    }

    private String sampleIdRef() {
        if (generatedIds.isEmpty()) {
            idCounter++;
            String id = "id" + idCounter;
            generatedIds.add(id);
            pendingIdAssignment = id;
            return id;
        }
        return generatedIds.getFirst();
    }

    private String sampleList(XSSimpleTypeDefinition type) {
        XSSimpleTypeDefinition itemType = type.getItemType();
        String item = sample(itemType);
        int minLength = intFacet(type, XSSimpleTypeDefinition.FACET_MINLENGTH, 1);
        int length = intFacet(type, XSSimpleTypeDefinition.FACET_LENGTH, -1);
        int count = length >= 0 ? length : Math.max(1, minLength);
        if (count <= 1) {
            return item;
        }
        var parts = new StringBuilder(item);
        for (int i = 1; i < count; i++) {
            parts.append(' ').append(sample(itemType));
        }
        return parts.toString();
    }

    private String sampleUnion(XSSimpleTypeDefinition type) {
        XSObjectList members = type.getMemberTypes();
        if (members != null) {
            for (int i = 0; i < members.getLength(); i++) {
                if (members.item(i) instanceof XSSimpleTypeDefinition member) {
                    return sample(member);
                }
            }
        }
        return "string";
    }

    private String sampleAtomic(XSSimpleTypeDefinition type) {
        short kind = builtInKind(type);

        return switch (kind) {
            case XSConstants.BOOLEAN_DT -> "true";
            case XSConstants.DECIMAL_DT, XSConstants.FLOAT_DT, XSConstants.DOUBLE_DT -> "1.0";
            case XSConstants.DURATION_DT -> "P1D";
            case XSConstants.DATETIME_DT -> "2020-01-01T00:00:00Z";
            case XSConstants.TIME_DT -> "00:00:00";
            case XSConstants.DATE_DT -> "2020-01-01";
            case XSConstants.GYEARMONTH_DT -> "2020-01";
            case XSConstants.GYEAR_DT -> "2020";
            case XSConstants.GMONTHDAY_DT -> "--01-01";
            case XSConstants.GDAY_DT -> "---01";
            case XSConstants.GMONTH_DT -> "--01";
            case XSConstants.HEXBINARY_DT -> "00";
            case XSConstants.BASE64BINARY_DT -> "YQ==";
            case XSConstants.ANYURI_DT -> "http://example.com";
            case XSConstants.QNAME_DT, XSConstants.NOTATION_DT -> "name";
            case XSConstants.INTEGER_DT, XSConstants.LONG_DT, XSConstants.INT_DT,
                 XSConstants.SHORT_DT, XSConstants.BYTE_DT,
                 XSConstants.POSITIVEINTEGER_DT, XSConstants.UNSIGNEDLONG_DT,
                 XSConstants.UNSIGNEDINT_DT, XSConstants.UNSIGNEDSHORT_DT,
                 XSConstants.UNSIGNEDBYTE_DT -> "1";
            case XSConstants.NONNEGATIVEINTEGER_DT -> "0";
            case XSConstants.NONPOSITIVEINTEGER_DT, XSConstants.NEGATIVEINTEGER_DT -> "-1";
            case XSConstants.ID_DT -> nextId();
            case XSConstants.IDREF_DT -> sampleIdRef();
            case XSConstants.ENTITY_DT -> "entity";
            case XSConstants.LIST_DT, XSConstants.LISTOFUNION_DT -> "string";
            default -> "string";
        };
    }

    private String applyFacets(XSSimpleTypeDefinition type, String raw) {
        short kind = builtInKind(type);
        if (isNumericKind(kind)) {
            return applyNumericFacets(type, raw, kind);
        }
        if (kind == XSConstants.HEXBINARY_DT) {
            return applyHexBinaryLengthFacets(type, raw);
        }
        if (kind == XSConstants.BASE64BINARY_DT) {
            return applyBase64BinaryLengthFacets(type);
        }
        return applyStringLengthFacets(type, raw);
    }

    private String applyStringLengthFacets(XSSimpleTypeDefinition type, String raw) {
        String value = raw == null ? "" : raw;
        int exact = intFacet(type, XSSimpleTypeDefinition.FACET_LENGTH, -1);
        int min = intFacet(type, XSSimpleTypeDefinition.FACET_MINLENGTH, -1);
        int max = intFacet(type, XSSimpleTypeDefinition.FACET_MAXLENGTH, -1);

        if (exact >= 0) {
            return fitLength(value, exact);
        }
        if (min >= 0 && value.length() < min) {
            value = fitLength(value, min);
        }
        if (max >= 0 && value.length() > max) {
            value = value.substring(0, max);
        }
        return value;
    }

    /**
     * XSD length facets on hexBinary are in octets (2 hex chars per octet).
     */
    private static String applyHexBinaryLengthFacets(XSSimpleTypeDefinition type, String raw) {
        String hex = (raw == null || raw.isBlank()) ? "00" : raw.replaceAll("\\s", "");
        if (hex.length() % 2 != 0) {
            hex = hex + "0";
        }
        int octets = hex.length() / 2;
        int exact = intFacet(type, XSSimpleTypeDefinition.FACET_LENGTH, -1);
        int min = intFacet(type, XSSimpleTypeDefinition.FACET_MINLENGTH, -1);
        int max = intFacet(type, XSSimpleTypeDefinition.FACET_MAXLENGTH, -1);
        int target = octets;
        if (exact >= 0) {
            target = exact;
        } else {
            if (min >= 0 && target < min) {
                target = min;
            }
            if (max >= 0 && target > max) {
                target = max;
            }
        }
        if (target <= 0) {
            return "";
        }
        var sb = new StringBuilder(target * 2);
        for (int i = 0; i < target; i++) {
            int idx = i * 2;
            if (idx + 1 < hex.length()) {
                sb.append(hex, idx, idx + 2);
            } else {
                sb.append("00");
            }
        }
        return sb.toString();
    }

    /**
     * XSD length facets on base64Binary are in octets. Emit a minimal base64 encoding of
     * {@code N} zero bytes (with standard padding).
     */
    private static String applyBase64BinaryLengthFacets(XSSimpleTypeDefinition type) {
        int exact = intFacet(type, XSSimpleTypeDefinition.FACET_LENGTH, -1);
        int min = intFacet(type, XSSimpleTypeDefinition.FACET_MINLENGTH, -1);
        int max = intFacet(type, XSSimpleTypeDefinition.FACET_MAXLENGTH, -1);
        int octets = 1;
        if (exact >= 0) {
            octets = exact;
        } else if (min >= 0) {
            octets = min;
        }
        if (max >= 0 && octets > max) {
            octets = max;
        }
        if (octets <= 0) {
            return "";
        }
        byte[] bytes = new byte[octets];
        return java.util.Base64.getEncoder().encodeToString(bytes);
    }

    private String applyNumericFacets(XSSimpleTypeDefinition type, String raw, short kind) {
        BigDecimal value;
        try {
            value = new BigDecimal(raw);
        } catch (NumberFormatException e) {
            return raw;
        }

        BigDecimal minInc = decimalFacet(type, XSSimpleTypeDefinition.FACET_MININCLUSIVE);
        BigDecimal maxInc = decimalFacet(type, XSSimpleTypeDefinition.FACET_MAXINCLUSIVE);
        BigDecimal minExc = decimalFacet(type, XSSimpleTypeDefinition.FACET_MINEXCLUSIVE);
        BigDecimal maxExc = decimalFacet(type, XSSimpleTypeDefinition.FACET_MAXEXCLUSIVE);
        int totalDigits = intFacet(type, XSSimpleTypeDefinition.FACET_TOTALDIGITS, -1);
        int fractionDigits = intFacet(type, XSSimpleTypeDefinition.FACET_FRACTIONDIGITS, -1);
        boolean integral = isIntegralKind(kind);
        BigDecimal step = integral ? BigDecimal.ONE : new BigDecimal("0.001");

        value = clampToBounds(value, minInc, maxInc, minExc, maxExc, step);

        if (fractionDigits >= 0) {
            value = value.setScale(fractionDigits, RoundingMode.HALF_UP);
            value = clampToBounds(value, minInc, maxInc, minExc, maxExc, step);
        }

        if (totalDigits >= 0 && digitCount(value) > totalDigits) {
            value = candidateWithinTotalDigits(totalDigits, fractionDigits, minInc, maxInc, minExc, maxExc, step, integral);
        }

        if (integral) {
            return value.toBigInteger().toString();
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private static BigDecimal clampToBounds(
            BigDecimal value,
            BigDecimal minInc,
            BigDecimal maxInc,
            BigDecimal minExc,
            BigDecimal maxExc,
            BigDecimal step) {
        BigDecimal result = value;
        if (minInc != null && result.compareTo(minInc) < 0) {
            result = minInc;
        }
        if (minExc != null && result.compareTo(minExc) <= 0) {
            result = minExc.add(step);
        }
        if (maxInc != null && result.compareTo(maxInc) > 0) {
            result = maxInc;
        }
        if (maxExc != null && result.compareTo(maxExc) >= 0) {
            result = maxExc.subtract(step);
        }
        // Re-check mins after max adjustments (narrow open intervals).
        if (minInc != null && result.compareTo(minInc) < 0) {
            result = minInc;
        }
        if (minExc != null && result.compareTo(minExc) <= 0) {
            // Prefer midpoint when the exclusive window is smaller than step*2.
            if (maxExc != null && minExc.compareTo(maxExc) < 0) {
                result = minExc.add(maxExc).divide(new BigDecimal("2"), 6, RoundingMode.HALF_UP);
            } else if (maxInc != null && minExc.compareTo(maxInc) < 0) {
                result = minExc.add(maxInc).divide(new BigDecimal("2"), 6, RoundingMode.HALF_UP);
            } else {
                result = minExc.add(step);
            }
        }
        return result;
    }

    private static BigDecimal candidateWithinTotalDigits(
            int totalDigits,
            int fractionDigits,
            BigDecimal minInc,
            BigDecimal maxInc,
            BigDecimal minExc,
            BigDecimal maxExc,
            BigDecimal step,
            boolean integral) {
        // Prefer the lower bound when present, else 1, then clamp and shrink if needed.
        BigDecimal seed = minInc != null ? minInc
                : minExc != null ? minExc.add(step)
                : BigDecimal.ONE;
        BigDecimal value = clampToBounds(seed, minInc, maxInc, minExc, maxExc, step);
        if (fractionDigits >= 0) {
            value = value.setScale(Math.clamp(totalDigits - 1, 0, fractionDigits), RoundingMode.HALF_UP);
        }
        if (digitCount(value) <= totalDigits) {
            return value;
        }
        // Fall back to the smallest positive value that fits totalDigits.
        if (totalDigits == 1 || integral) {
            value = BigDecimal.ONE;
        } else {
            int frac = fractionDigits >= 0 ? Math.min(fractionDigits, totalDigits - 1) : Math.min(1, totalDigits - 1);
            value = BigDecimal.ONE.movePointLeft(Math.max(frac, 0));
            if (frac >= 0) {
                value = value.setScale(frac, RoundingMode.HALF_UP);
            }
        }
        return clampToBounds(value, minInc, maxInc, minExc, maxExc, step);
    }

    private static int digitCount(BigDecimal value) {
        String plain = value.stripTrailingZeros().toPlainString().replace("-", "").replace(".", "");
        if (plain.isEmpty() || plain.equals("0")) {
            return 1;
        }
        // strip leading zeros after decimal normalization
        int i = 0;
        while (i < plain.length() - 1 && plain.charAt(i) == '0') {
            i++;
        }
        return plain.length() - i;
    }

    private static BigDecimal decimalFacet(XSSimpleTypeDefinition type, short facet) {
        if (!type.isDefinedFacet(facet)) {
            return null;
        }
        String lexical = type.getLexicalFacetValue(facet);
        if (lexical == null || lexical.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(lexical);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String fitLength(String value, int length) {
        if (value.length() == length) {
            return value;
        }
        if (value.length() > length) {
            return value.substring(0, length);
        }
        var sb = new StringBuilder(value);
        while (sb.length() < length) {
            sb.append('x');
        }
        return sb.toString();
    }

    private static int intFacet(XSSimpleTypeDefinition type, short facet, int defaultValue) {
        if (!type.isDefinedFacet(facet)) {
            return defaultValue;
        }
        String lexical = type.getLexicalFacetValue(facet);
        if (lexical == null || lexical.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(lexical);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static short builtInKind(XSSimpleTypeDefinition type) {
        short kind = type.getBuiltInKind();
        if (kind == XSConstants.UNAVAILABLE_DT || kind == 0) {
            XSSimpleTypeDefinition primitive = type.getPrimitiveType();
            if (primitive != null) {
                kind = primitive.getBuiltInKind();
            }
        }
        return kind;
    }

    private static boolean isNumericKind(short kind) {
        return kind == XSConstants.DECIMAL_DT
                || kind == XSConstants.FLOAT_DT
                || kind == XSConstants.DOUBLE_DT
                || isIntegralKind(kind);
    }

    private static boolean isIntegralKind(short kind) {
        return kind == XSConstants.INTEGER_DT
                || kind == XSConstants.LONG_DT
                || kind == XSConstants.INT_DT
                || kind == XSConstants.SHORT_DT
                || kind == XSConstants.BYTE_DT
                || kind == XSConstants.POSITIVEINTEGER_DT
                || kind == XSConstants.NONNEGATIVEINTEGER_DT
                || kind == XSConstants.NONPOSITIVEINTEGER_DT
                || kind == XSConstants.NEGATIVEINTEGER_DT
                || kind == XSConstants.UNSIGNEDLONG_DT
                || kind == XSConstants.UNSIGNEDINT_DT
                || kind == XSConstants.UNSIGNEDSHORT_DT
                || kind == XSConstants.UNSIGNEDBYTE_DT;
    }
}
