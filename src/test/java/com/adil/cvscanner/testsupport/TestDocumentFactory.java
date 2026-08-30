package com.adil.cvscanner.testsupport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class TestDocumentFactory {

    private TestDocumentFactory() {
    }

    public static Path createPdf(
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

        Files.createDirectories(
                target.getParent()
        );

        Files.write(
                target,
                output.toByteArray()
        );

        return target;
    }

    public static Path createDocx(
            Path target,
            String... paragraphs
    ) throws IOException {

        Files.createDirectories(
                target.getParent()
        );

        try (
                ZipOutputStream zip =
                        new ZipOutputStream(
                                Files.newOutputStream(
                                        target
                                )
                        )
        ) {

            writeZipEntry(
                    zip,
                    "[Content_Types].xml",
                    """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                        <Default
                            Extension="rels"
                            ContentType="application/vnd.openxmlformats-package.relationships+xml"/>

                        <Default
                            Extension="xml"
                            ContentType="application/xml"/>

                        <Override
                            PartName="/word/document.xml"
                            ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                    </Types>
                    """
            );

            writeZipEntry(
                    zip,
                    "_rels/.rels",
                    """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships
                        xmlns="http://schemas.openxmlformats.org/package/2006/relationships">

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

        return target;
    }

    private static String buildPdfContent(
            String... lines
    ) {

        StringBuilder content =
                new StringBuilder();

        content.append("BT\n");
        content.append("/F1 12 Tf\n");
        content.append("72 720 Td\n");

        for (
                int index = 0;
                index < lines.length;
                index++
        ) {

            if (index > 0) {
                content.append(
                        "0 -18 Td\n"
                );
            }

            content
                    .append("(")
                    .append(
                            escapePdfText(
                                    lines[index]
                            )
                    )
                    .append(") Tj\n");
        }

        content.append("ET\n");

        return content.toString();
    }

    private static void writePdf(
            ByteArrayOutputStream output,
            String value
    ) throws IOException {

        output.write(
                value.getBytes(
                        StandardCharsets.ISO_8859_1
                )
        );
    }

    private static void writeZipEntry(
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

    private static String escapePdfText(
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

    private static String escapeXml(
            String value
    ) {

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
