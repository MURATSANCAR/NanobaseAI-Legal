package com.nanobase.specai.document.application;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

@Component
public class FileTypeInspector {
    private static final Set<String> ALLOWED = Set.of(
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    private final Tika tika = new Tika();

    public String inspect(InputStream content, String filename) {
        try {
            String detected = tika.detect(content, filename);
            if (!ALLOWED.contains(detected)) {
                throw new InvalidDocumentException("File content is not a supported PDF or DOCX");
            }
            return detected;
        } catch (IOException exception) {
            throw new InvalidDocumentException("File content could not be inspected");
        }
    }
}
