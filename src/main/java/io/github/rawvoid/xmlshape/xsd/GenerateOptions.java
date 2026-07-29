package io.github.rawvoid.xmlshape.xsd;

/**
 * Options controlling XSD-driven XML instance generation.
 *
 * @param choiceStrategy              how {@code xs:choice} (and abstract substitution groups) are expanded
 * @param includeOptionalElements     when {@code true}, emit particles with {@code minOccurs=0}
 * @param includeOptionalAttributes   when {@code true}, emit attributes that are not required
 * @param repeatingParticleCount      instances to emit for particles with {@code maxOccurs > 1}
 * @param emitWildcardPlaceholders    when {@code true}, emit placeholders for {@code xs:any}/{@code anyAttribute}
 */
public record GenerateOptions(
        ChoiceStrategy choiceStrategy,
        boolean includeOptionalElements,
        boolean includeOptionalAttributes,
        int repeatingParticleCount,
        boolean emitWildcardPlaceholders
) {
    public GenerateOptions {
        if (choiceStrategy == null) {
            throw new IllegalArgumentException("choiceStrategy must not be null");
        }
        if (repeatingParticleCount < 1) {
            throw new IllegalArgumentException("repeatingParticleCount must be >= 1");
        }
    }

    public static GenerateOptions defaults() {
        return new GenerateOptions(ChoiceStrategy.ALL, true, true, 1, false);
    }

    public GenerateOptions withChoiceStrategy(ChoiceStrategy strategy) {
        return new GenerateOptions(strategy, includeOptionalElements, includeOptionalAttributes,
                repeatingParticleCount, emitWildcardPlaceholders);
    }

    public GenerateOptions withIncludeOptionalElements(boolean include) {
        return new GenerateOptions(choiceStrategy, include, includeOptionalAttributes,
                repeatingParticleCount, emitWildcardPlaceholders);
    }

    public GenerateOptions withIncludeOptionalAttributes(boolean include) {
        return new GenerateOptions(choiceStrategy, includeOptionalElements, include,
                repeatingParticleCount, emitWildcardPlaceholders);
    }

    public GenerateOptions withRepeatingParticleCount(int count) {
        return new GenerateOptions(choiceStrategy, includeOptionalElements, includeOptionalAttributes,
                count, emitWildcardPlaceholders);
    }

    public GenerateOptions withEmitWildcardPlaceholders(boolean emit) {
        return new GenerateOptions(choiceStrategy, includeOptionalElements, includeOptionalAttributes,
                repeatingParticleCount, emit);
    }
}
