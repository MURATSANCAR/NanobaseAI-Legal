package com.nanobase.specai.document.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nanobase.specai.document.application.InvalidDocumentException;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ArchiveSafetyInspectorTest {
    private final ArchiveSafetyInspector inspector =
        new ArchiveSafetyInspector(10, 1024, 20, 20);

    @Test
    void rejectsNestedArchiveInsideDocx() throws Exception {
        byte[] docx;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("word/embeddings/payload.zip"));
            zip.write(new byte[] {1, 2, 3});
            zip.closeEntry();
            zip.finish();
            docx = bytes.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile("file", "unsafe.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx);

        assertThatThrownBy(() -> inspector.inspect(file,
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
            .isInstanceOf(InvalidDocumentException.class)
            .hasMessageContaining("Nested archives");
    }

    @Test
    void rejectsEncryptedPdfBeforeParserRouting() {
        MockMultipartFile file = new MockMultipartFile("file", "encrypted.pdf",
            "application/pdf", "%PDF-1.7\n1 0 obj\n/Encrypt 2 0 R".getBytes());

        assertThatThrownBy(() -> inspector.inspect(file, "application/pdf"))
            .isInstanceOf(InvalidDocumentException.class)
            .hasMessageContaining("Encrypted PDF");
    }

    @Test
    void acceptsBoundedDigitalPdf() {
        MockMultipartFile file = new MockMultipartFile("file", "safe.pdf",
            "application/pdf", "%PDF-1.7\n/Type /Page\n%%EOF".getBytes());

        assertThat(inspector.inspect(file, "application/pdf"))
            .containsEntry("encrypted", false)
            .containsEntry("estimatedPageCount", 1);
    }
}
