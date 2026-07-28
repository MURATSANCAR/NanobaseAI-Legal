package com.nanobase.specai.workflow.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Component;

public final class SafeReportRenderers {
    private SafeReportRenderers() {
    }

    @Component
    public static class PdfRenderer implements ReportFormatRenderer {
        @Override
        public boolean supports(String formatConceptCode) {
            return "PDF".equals(formatConceptCode);
        }

        @Override
        public RenderedReport render(String reportName, JsonNode report) {
            List<String> lines = plainLines(reportName, report);
            StringBuilder stream = new StringBuilder("BT /F1 10 Tf 42 800 Td ");
            int line = 0;
            for (String value : lines.stream().limit(48).toList()) {
                if (line++ > 0) {
                    stream.append("0 -15 Td ");
                }
                stream.append('(').append(pdfEscape(ascii(value))).append(") Tj ");
            }
            stream.append("ET");
            byte[] content = stream.toString().getBytes(StandardCharsets.US_ASCII);
            List<byte[]> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>".getBytes(StandardCharsets.US_ASCII),
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>".getBytes(StandardCharsets.US_ASCII),
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                    .concat("/Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>")
                    .getBytes(StandardCharsets.US_ASCII),
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"
                    .getBytes(StandardCharsets.US_ASCII),
                ("<< /Length " + content.length + " >>\nstream\n"
                    + new String(content, StandardCharsets.US_ASCII) + "\nendstream")
                    .getBytes(StandardCharsets.US_ASCII));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            write(output, "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII));
            List<Integer> offsets = new ArrayList<>();
            for (int index = 0; index < objects.size(); index++) {
                offsets.add(output.size());
                write(output, ((index + 1) + " 0 obj\n").getBytes(StandardCharsets.US_ASCII));
                write(output, objects.get(index));
                write(output, "\nendobj\n".getBytes(StandardCharsets.US_ASCII));
            }
            int xref = output.size();
            write(output, ("xref\n0 " + (objects.size() + 1) + "\n"
                + "0000000000 65535 f \n").getBytes(StandardCharsets.US_ASCII));
            offsets.forEach(offset -> write(output,
                String.format("%010d 00000 n \n", offset).getBytes(StandardCharsets.US_ASCII)));
            write(output, ("trailer << /Size " + (objects.size() + 1)
                + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF")
                .getBytes(StandardCharsets.US_ASCII));
            return new RenderedReport(output.toByteArray(), "pdf", "application/pdf");
        }
    }

    @Component
    public static class DocxRenderer implements ReportFormatRenderer {
        @Override
        public boolean supports(String formatConceptCode) {
            return "DOCX".equals(formatConceptCode);
        }

        @Override
        public RenderedReport render(String reportName, JsonNode report) {
            List<String> lines = plainLines(reportName, report);
            StringBuilder body = new StringBuilder();
            lines.forEach(line -> body.append("<w:p><w:r><w:t xml:space=\"preserve\">")
                .append(xml(line)).append("</w:t></w:r></w:p>"));
            String document = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                + "<w:body>" + body + "<w:sectPr/></w:body></w:document>";
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(output)) {
                entry(zip, "[Content_Types].xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                        + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                        + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                        + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                        + "</Types>");
                entry(zip, "_rels/.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                        + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
                        + "</Relationships>");
                entry(zip, "word/document.xml", document);
            } catch (IOException exception) {
                throw new IllegalStateException("DOCX rendering failed", exception);
            }
            return new RenderedReport(output.toByteArray(), "docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        }
    }

    @Component
    public static class XlsxRenderer implements ReportFormatRenderer {
        @Override
        public boolean supports(String formatConceptCode) {
            return "XLSX".equals(formatConceptCode);
        }

        @Override
        public RenderedReport render(String reportName, JsonNode report) {
            List<String> lines = plainLines(reportName, report);
            StringBuilder rows = new StringBuilder();
            for (int index = 0; index < lines.size(); index++) {
                rows.append("<row r=\"").append(index + 1).append("\"><c r=\"A")
                    .append(index + 1).append("\" t=\"inlineStr\"><is><t>")
                    .append(xml(lines.get(index))).append("</t></is></c></row>");
            }
            String worksheet = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<sheetData>" + rows + "</sheetData></worksheet>";
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(output)) {
                entry(zip, "[Content_Types].xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                        + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                        + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                        + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                        + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                        + "</Types>");
                entry(zip, "_rels/.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                        + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                        + "</Relationships>");
                entry(zip, "xl/workbook.xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                        + "<sheets><sheet name=\"Report\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
                entry(zip, "xl/_rels/workbook.xml.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                        + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                        + "</Relationships>");
                entry(zip, "xl/worksheets/sheet1.xml", worksheet);
            } catch (IOException exception) {
                throw new IllegalStateException("XLSX rendering failed", exception);
            }
            return new RenderedReport(output.toByteArray(), "xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        }
    }

    private static List<String> plainLines(String reportName, JsonNode report) {
        List<String> lines = new ArrayList<>();
        lines.add(reportName);
        lines.add("Snapshot: " + report.path("snapshotId").asText());
        for (JsonNode section : report.path("sections")) {
            lines.add("");
            lines.add(section.path("title").asText(section.path("code").asText()));
            JsonNode data = section.path("data");
            if (data.isObject()) {
                data.fields().forEachRemaining(field ->
                    lines.add(field.getKey() + ": " + compact(field.getValue())));
            } else {
                lines.add(compact(data));
            }
        }
        return List.copyOf(lines);
    }

    private static String compact(JsonNode value) {
        String text = value.isValueNode() ? value.asText() : value.toString();
        return text.length() > 500 ? text.substring(0, 500) + "..." : text;
    }

    private static void entry(ZipOutputStream zip, String name, String content)
        throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String xml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;")
            .replace("'", "&apos;");
    }

    private static String ascii(String value) {
        return value.replaceAll("[^\\x20-\\x7E]", "?");
    }

    private static String pdfEscape(String value) {
        return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private static void write(ByteArrayOutputStream output, byte[] bytes) {
        output.writeBytes(bytes);
    }
}
