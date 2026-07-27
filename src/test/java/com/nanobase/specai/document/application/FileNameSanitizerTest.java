package com.nanobase.specai.document.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FileNameSanitizerTest {
    @Test
    void removesPathsControlCharactersAndUnsafeCharacters() {
        assertThat(FileNameSanitizer.sanitize("../../teknik:\nşartname?.pdf"))
            .isEqualTo("teknik__şartname_.pdf");
    }

    @Test
    void preservesExtensionWhenTruncating() {
        String result = FileNameSanitizer.sanitize("a".repeat(300) + ".docx");
        assertThat(result).hasSize(180).endsWith(".docx");
    }
}
