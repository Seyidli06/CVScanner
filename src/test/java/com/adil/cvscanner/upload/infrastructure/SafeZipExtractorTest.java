package com.adil.cvscanner.upload.infrastructure;

import com.adil.cvscanner.upload.application.InvalidUploadException;
import com.adil.cvscanner.upload.application.ZipExtractionResult;
import com.adil.cvscanner.upload.config.UploadStorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SafeZipExtractorTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldExtractPdfAndDocxFiles() throws IOException {

        SafeZipExtractor extractor =
                createExtractor(
                        100,
                        DataSize.ofMegabytes(100),
                        DataSize.ofMegabytes(10)
                );

        Path archive = createZip(
                Map.of(
                        "john.pdf", "fake-pdf".getBytes(),
                        "jane.docx", "fake-docx".getBytes()
                )
        );

        Path extractionDir =
                tempDir.resolve("extracted");

        ZipExtractionResult result =
                extractor.extract(
                        archive,
                        extractionDir
                );

        assertThat(result.fileCount())
                .isEqualTo(2);

        assertThat(
                extractionDir.resolve("john.pdf")
        ).exists();

        assertThat(
                extractionDir.resolve("jane.docx")
        ).exists();
    }

    @Test
    void shouldRejectZipSlipEntry() throws IOException {

        SafeZipExtractor extractor =
                createDefaultExtractor();

        Path archive = createZip(
                Map.of(
                        "../../evil.pdf",
                        "malicious".getBytes()
                )
        );

        Path extractionDir =
                tempDir.resolve("extracted");

        assertThatThrownBy(
                () -> extractor.extract(
                        archive,
                        extractionDir
                )
        )
                .isInstanceOf(InvalidUploadException.class)
                .hasMessage("ZIP contains an unsafe path");

        assertThat(
                tempDir.resolve("evil.pdf")
        ).doesNotExist();

        assertThat(extractionDir)
                .doesNotExist();
    }

    @Test
    void shouldRejectUnsupportedFileType() throws IOException {

        SafeZipExtractor extractor =
                createDefaultExtractor();

        Path archive = createZip(
                Map.of(
                        "virus.exe",
                        "malicious".getBytes()
                )
        );

        Path extractionDir =
                tempDir.resolve("extracted");

        assertThatThrownBy(
                () -> extractor.extract(
                        archive,
                        extractionDir
                )
        )
                .isInstanceOf(InvalidUploadException.class)
                .hasMessage(
                        "ZIP may contain only PDF and DOCX files"
                );

        assertThat(extractionDir)
                .doesNotExist();
    }

    @Test
    void shouldRejectEmptyZip() throws IOException {

        SafeZipExtractor extractor =
                createDefaultExtractor();

        Path archive = createZip(Map.of());

        Path extractionDir =
                tempDir.resolve("extracted");

        assertThatThrownBy(
                () -> extractor.extract(
                        archive,
                        extractionDir
                )
        )
                .isInstanceOf(InvalidUploadException.class)
                .hasMessage(
                        "ZIP does not contain any supported CV files"
                );

        assertThat(extractionDir)
                .doesNotExist();
    }

    @Test
    void shouldRejectZipWithTooManyEntries() throws IOException {

        SafeZipExtractor extractor =
                createExtractor(
                        2,
                        DataSize.ofMegabytes(100),
                        DataSize.ofMegabytes(10)
                );

        Map<String, byte[]> entries =
                new LinkedHashMap<>();

        entries.put("one.pdf", "1".getBytes());
        entries.put("two.pdf", "2".getBytes());
        entries.put("three.pdf", "3".getBytes());

        Path archive = createZip(entries);

        Path extractionDir =
                tempDir.resolve("extracted");

        assertThatThrownBy(
                () -> extractor.extract(
                        archive,
                        extractionDir
                )
        )
                .isInstanceOf(InvalidUploadException.class)
                .hasMessage(
                        "ZIP contains too many entries"
                );

        assertThat(extractionDir)
                .doesNotExist();
    }

    @Test
    void shouldRejectFileExceedingSingleFileLimit()
            throws IOException {

        SafeZipExtractor extractor =
                createExtractor(
                        100,
                        DataSize.ofMegabytes(100),
                        DataSize.ofBytes(10)
                );

        byte[] content = new byte[20];

        Path archive = createZip(
                Map.of(
                        "large.pdf",
                        content
                )
        );

        Path extractionDir =
                tempDir.resolve("extracted");

        assertThatThrownBy(
                () -> extractor.extract(
                        archive,
                        extractionDir
                )
        )
                .isInstanceOf(InvalidUploadException.class)
                .hasMessage(
                        "A CV exceeds the maximum allowed size"
                );

        assertThat(extractionDir)
                .doesNotExist();
    }

    @Test
    void shouldRejectArchiveExceedingTotalExtractedLimit()
            throws IOException {

        SafeZipExtractor extractor =
                createExtractor(
                        100,
                        DataSize.ofBytes(10),
                        DataSize.ofBytes(100)
                );

        Path archive = createZip(
                Map.of(
                        "one.pdf", new byte[6],
                        "two.pdf", new byte[6]
                )
        );

        Path extractionDir =
                tempDir.resolve("extracted");

        assertThatThrownBy(
                () -> extractor.extract(
                        archive,
                        extractionDir
                )
        )
                .isInstanceOf(InvalidUploadException.class)
                .hasMessage(
                        "ZIP expands beyond the maximum allowed size"
                );

        assertThat(extractionDir)
                .doesNotExist();
    }

    private SafeZipExtractor createDefaultExtractor() {

        return createExtractor(
                100,
                DataSize.ofMegabytes(100),
                DataSize.ofMegabytes(10)
        );
    }

    private SafeZipExtractor createExtractor(
            int maxEntries,
            DataSize maxExtractedSize,
            DataSize maxSingleFileSize
    ) {

        UploadStorageProperties properties =
                new UploadStorageProperties(
                        tempDir.resolve("storage"),
                        maxEntries,
                        maxExtractedSize,
                        maxSingleFileSize
                );

        return new SafeZipExtractor(properties);
    }

    private Path createZip(
            Map<String, byte[]> entries
    ) throws IOException {

        Path archive =
                tempDir.resolve(
                        "archive-" +
                                System.nanoTime() +
                                ".zip"
                );

        try (
                ZipOutputStream output =
                        new ZipOutputStream(
                                Files.newOutputStream(archive)
                        )
        ) {

            for (
                    Map.Entry<String, byte[]> entry
                    : entries.entrySet()
            ) {

                output.putNextEntry(
                        new ZipEntry(entry.getKey())
                );

                output.write(entry.getValue());

                output.closeEntry();
            }
        }

        return archive;
    }
}