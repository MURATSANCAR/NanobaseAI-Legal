package com.nanobase.specai.document.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;

class DocumentHashTest {
    @Test
    void calculatesStableSha256() {
        assertThat(DocumentService.sha256(new ByteArrayInputStream("specai".getBytes())))
            .isEqualTo("55ad834e655648a51bcd7e7522a6d64b7013b8c586e0b2f253b9a0427e89c4b2");
    }
}
