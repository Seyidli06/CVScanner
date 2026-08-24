package com.adil.cvscanner.processing.infrastructure;

import com.adil.cvscanner.processing.application.DocumentErrorCode;
import com.adil.cvscanner.processing.application.DocumentParsingException;
import com.adil.cvscanner.processing.application.ParsedDocument;
import com.adil.cvscanner.processing.config.DocumentParsingProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TikaDocumentTextExtractorTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldRejectFakePdf() throws IOException {

        Path fakePdf =
                tempDir.resolve("candidate.pdf");

        Files.writeString(
                fakePdf,
                "This is not a real PDF file"
        );

        TikaDocumentTextExtractor extractor =
                createExtractor();

        assertThatThrownBy(
                () -> extractor.extract(fakePdf)
        )
                .isInstanceOfSatisfying(
                        DocumentParsingException.class,
                        exception ->
                                assertThat(
                                        exception.getCode()
                                ).isEqualTo(
                                        DocumentErrorCode
                                                .UNSUPPORTED_MEDIA_TYPE
                                )
                );
    }

    @Test
    void shouldRejectMissingFile() {

        Path missing =
                tempDir.resolve(
                        "does-not-exist.pdf"
                );

        TikaDocumentTextExtractor extractor =
                createExtractor();

        assertThatThrownBy(
                () -> extractor.extract(missing)
        )
                .isInstanceOfSatisfying(
                        DocumentParsingException.class,
                        exception ->
                                assertThat(
                                        exception.getCode()
                                ).isEqualTo(
                                        DocumentErrorCode
                                                .FILE_NOT_FOUND
                                )
                );
    }

    @Test
    void shouldExtractTextFromRealPdf()
            throws IOException {

        Path pdf =
                tempDir.resolve(
                        "john-smith.pdf"
                );

        createRealPdf(
                pdf,
                "John Smith",
                "Java Backend Developer",
                "5 years experience",
                "Spring Boot PostgreSQL",
                "Remote Baku"
        );

        TikaDocumentTextExtractor extractor =
                createExtractor();

        ParsedDocument result =
                extractor.extract(pdf);

        assertThat(result.source())
                .isEqualTo(pdf);

        assertThat(result.mediaType())
                .isEqualTo(
                        "application/pdf"
                );

        assertThat(result.text())
                .contains(
                        "John Smith",
                        "Java Backend Developer",
                        "5 years experience",
                        "Spring Boot PostgreSQL",
                        "Remote Baku"
                );
    }

    @Test
    void shouldExtractTextFromRealDocx()
            throws IOException {

        Path docx =
                tempDir.resolve(
                        "jane-doe.docx"
                );

        createRealDocx(
                docx,
                "Jane Doe",
                "Senior Java Developer",
                "7 years experience",
                "Java Spring Boot Redis",
                "Hybrid Baku"
        );

        TikaDocumentTextExtractor extractor =
                createExtractor();

        ParsedDocument result =
                extractor.extract(docx);

        assertThat(result.source())
                .isEqualTo(docx);

        assertThat(result.mediaType())
                .isEqualTo(
                        "application/vnd.openxmlformats-officedocument."
                                + "wordprocessingml.document"
                );

        assertThat(result.text())
                .contains(
                        "Jane Doe",
                        "Senior Java Developer",
                        "7 years experience",
                        "Java Spring Boot Redis",
                        "Hybrid Baku"
                );
    }

    private TikaDocumentTextExtractor createExtractor() {

        DocumentParsingProperties properties =
                new DocumentParsingProperties(
                        1_000_000
                );

        return new TikaDocumentTextExtractor(
                properties
        );
    }

    private void createRealDocx(
            Path target,
            String... paragraphs
    ) throws IOException {

        try (
                ZipOutputStream zip =
                        new ZipOutputStream(
                                Files.newOutputStream(target)
                        )
        ) {

            writeZipEntry(
                    zip,
                    "[Content_Types].xml",
                    """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                        <Default Extension="rels"
                                 ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                        <Default Extension="xml"
                                 ContentType="application/xml"/>
                        <Override PartName="/word/document.xml"
                                  ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                    </Types>
                    """
            );

            writeZipEntry(
                    zip,
                    "_rels/.rels",
                    """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                        <Relationship
                            Id="rId1"
                            Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
                            Target="word/document.xml"/>
                    </Relationships>
                    """
            );

            StringBuilder body =
                    new StringBuilder();

            for (String paragraph : paragraphs) {

                body.append(
                        """
                        <w:p>
                            <w:r>
                                <w:t xml:space="preserve">
                        """
                );

                body.append(
                        escapeXml(paragraph)
                );

                body.append(
                        """
                                </w:t>
                            </w:r>
                        </w:p>
                        """
                );
            }

            String documentXml =
                    """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <w:document
                        xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                        <w:body>
                    """
                            + body
                            + """
                        </w:body>
                    </w:document>
                    """;

            writeZipEntry(
                    zip,
                    "word/document.xml",
                    documentXml
            );
        }
    }

    private void writeZipEntry(
            ZipOutputStream zip,
            String name,
            String content
    ) throws IOException {

        zip.putNextEntry(
                new ZipEntry(name)
        );

        zip.write(
                content.getBytes(
                        StandardCharsets.UTF_8
                )
        );

        zip.closeEntry();
    }

    private void createRealPdf(
            Path target,
            String... lines
    ) throws IOException {

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        List<Integer> offsets =
                new ArrayList<>();

        writePdf(
                output,
                "%PDF-1.4\n"
        );

        offsets.add(output.size());

        writePdf(
                output,
                """
                1 0 obj
                << /Type /Catalog /Pages 2 0 R >>
                endobj
                """
        );

        offsets.add(output.size());

        writePdf(
                output,
                """
                2 0 obj
                << /Type /Pages /Kids [3 0 R] /Count 1 >>
                endobj
                """
        );

        offsets.add(output.size());

        writePdf(
                output,
                """
                3 0 obj
                <<
                    /Type /Page
                    /Parent 2 0 R
                    /MediaBox [0 0 612 792]
                    /Resources <<
                        /Font <<
                            /F1 4 0 R
                        >>
                    >>
                    /Contents 5 0 R
                >>
                endobj
                """
        );

        offsets.add(output.size());

        writePdf(
                output,
                """
                4 0 obj
                <<
                    /Type /Font
                    /Subtype /Type1
                    /BaseFont /Helvetica
                >>
                endobj
                """
        );

        String contentStream =
                buildPdfContent(lines);

        byte[] streamBytes =
                contentStream.getBytes(
                        StandardCharsets.ISO_8859_1
                );

        offsets.add(output.size());

        writePdf(
                output,
                "5 0 obj\n"
                        + "<< /Length "
                        + streamBytes.length
                        + " >>\n"
                        + "stream\n"
        );

        output.write(streamBytes);

        writePdf(
                output,
                "\nendstream\n"
                        + "endobj\n"
        );

        int xrefOffset =
                output.size();

        writePdf(
                output,
                "xref\n"
                        + "0 6\n"
                        + "0000000000 65535 f \n"
        );

        for (int offset : offsets) {

            writePdf(
                    output,
                    String.format(
                            "%010d 00000 n \n",
                            offset
                    )
            );
        }

        writePdf(
                output,
                "trailer\n"
                        + "<< /Size 6 /Root 1 0 R >>\n"
                        + "startxref\n"
                        + xrefOffset
                        + "\n%%EOF\n"
        );

        Files.write(
                target,
                output.toByteArray()
        );
    }

    private String buildPdfContent(
            String... lines
    ) {

        StringBuilder content =
                new StringBuilder();

        content.append(
                "BT\n"
        );

        content.append(
                "/F1 12 Tf\n"
        );

        content.append(
                "72 720 Td\n"
        );

        for (int i = 0; i < lines.length; i++) {

            if (i > 0) {
                content.append(
                        "0 -18 Td\n"
                );
            }

            content.append("(")
                    .append(
                            escapePdfText(
                                    lines[i]
                            )
                    )
                    .append(") Tj\n");
        }

        content.append(
                "ET\n"
        );

        return content.toString();
    }

    private void writePdf(
            ByteArrayOutputStream output,
            String value
    ) throws IOException {

        output.write(
                value.getBytes(
                        StandardCharsets.ISO_8859_1
                )
        );
    }

    private String escapePdfText(
            String value
    ) {

        return value
                .replace(
                        "\\",
                        "\\\\"
                )
                .replace(
                        "(",
                        "\\("
                )
                .replace(
                        ")",
                        "\\)"
                );
    }

    private String escapeXml(
            String value
    ) {

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    @Test
    void shouldRejectEmptyPdf() throws IOException {

        Path pdf =
                tempDir.resolve(
                        "empty.pdf"
                );

        createRealPdf(pdf);

        TikaDocumentTextExtractor extractor =
                createExtractor();

        assertThatThrownBy(
                () -> extractor.extract(pdf)
        )
                .isInstanceOfSatisfying(
                        DocumentParsingException.class,
                        exception ->
                                assertThat(
                                        exception.getCode()
                                ).isEqualTo(
                                        DocumentErrorCode
                                                .EMPTY_DOCUMENT
                                )
                );
    }

    @Test
    void shouldRejectDocumentWhenExtractedTextExceedsLimit()
            throws IOException {

        Path pdf =
                tempDir.resolve(
                        "large-text.pdf"
                );

        createRealPdf(
                pdf,
                "John Smith Java Backend Developer "
                        + "Spring Boot PostgreSQL Redis Docker "
                        + "Microservices Kafka Kubernetes"
        );

        TikaDocumentTextExtractor extractor =
                new TikaDocumentTextExtractor(
                        new DocumentParsingProperties(
                                20
                        )
                );

        assertThatThrownBy(
                () -> extractor.extract(pdf)
        )
                .isInstanceOfSatisfying(
                        DocumentParsingException.class,
                        exception ->
                                assertThat(
                                        exception.getCode()
                                ).isEqualTo(
                                        DocumentErrorCode
                                                .TEXT_LIMIT_EXCEEDED
                                )
                );
    }

    @Test
    void shouldRejectCorruptedPdf() throws IOException {

        Path pdf =
                tempDir.resolve(
                        "corrupted.pdf"
                );

        Files.write(
                pdf,
                """
                %PDF-1.4
                1 0 obj
                << /Type /Catalog
                BROKEN PDF CONTENT
                """.getBytes(
                        StandardCharsets.ISO_8859_1
                )
        );

        TikaDocumentTextExtractor extractor =
                createExtractor();

        assertThatThrownBy(
                () -> extractor.extract(pdf)
        )
                .isInstanceOfSatisfying(
                        DocumentParsingException.class,
                        exception ->
                                assertThat(
                                        exception.getCode()
                                ).isEqualTo(
                                        DocumentErrorCode
                                                .PARSE_FAILED
                                )
                );
    }


}