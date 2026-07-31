package com.nanobase.specai.document.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DefaultNumericOcrIntegrityValidatorTest {

    private final DefaultNumericOcrIntegrityValidator validator =
        new DefaultNumericOcrIntegrityValidator();

    @Test
    void flagsLookalikeNumericTokensWithoutCorrectingThem() {
        NumericOcrValidationResult result = validator.validate(
            new NumericOcrContext("Koruma sinifi IP6S ve basinc 1O bar", 0.8d, "page-3"));
        assertThat(result.ambiguous()).isTrue();
        assertThat(result.issues()).isNotEmpty();
        assertThat(result.confidencePenalty()).isGreaterThan(0d);
    }

    @Test
    void cleanNumericTextIsNotAmbiguous() {
        NumericOcrValidationResult result = validator.validate(
            new NumericOcrContext("Minimum basinc 10 bar, IP65", 0.92d, "page-1"));
        assertThat(result.ambiguous()).isFalse();
        assertThat(result.issues()).isEmpty();
    }
}
