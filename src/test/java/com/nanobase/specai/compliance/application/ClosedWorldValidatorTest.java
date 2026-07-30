package com.nanobase.specai.compliance.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClosedWorldValidatorTest {
    @Test
    void modelCannotForceClosedWorldWithoutDeclaration() {
        var validator = new ClosedWorldValidator(null);
        assertThat(validator.acceptClosedWorldClaim(true, false)).isFalse();
        assertThat(validator.acceptClosedWorldClaim(true, true)).isTrue();
        assertThat(validator.acceptClosedWorldClaim(false, true)).isFalse();
    }
}
