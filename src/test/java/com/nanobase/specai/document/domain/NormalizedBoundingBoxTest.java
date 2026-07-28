package com.nanobase.specai.document.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NormalizedBoundingBoxTest {
    @Test
    void normalizesProviderCoordinatesAndAppliesRotation() {
        NormalizedBoundingBox box = NormalizedBoundingBox.fromProvider(
            2, 20, 10, 120, 60, 200, 100, 90);
        assertThat(box.page()).isEqualTo(2);
        assertThat(box.x()).isEqualTo(0.4);
        assertThat(box.y()).isEqualTo(0.1);
        assertThat(box.width()).isEqualTo(0.5);
        assertThat(box.height()).isEqualTo(0.5);
    }

    @Test
    void rejectsCoordinatesOutsideNormalizedPage() {
        assertThatThrownBy(() ->
            new NormalizedBoundingBox(1, .8, .2, .3, .1))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
