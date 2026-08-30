package com.adil.cvscanner.upload.infrastructure;

import com.adil.cvscanner.upload.application.InvalidUploadException;
import com.adil.cvscanner.upload.application.ZipExtractionResult;
import com.adil.cvscanner.upload.config.UploadStorageProperties;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

@Component
public class SafeZipExtractor {

    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of(".pdf", ".docx");

    private static final int BUFFER_SIZE = 8192;

    private final UploadStorageProperties properties;

    public SafeZipExtractor(
            UploadStorageProperties properties
    ) {
        this.properties = properties;
    }

    public ZipExtractionResult extract(
            Path archive,
            Path extractionDirectory
    ) {

        Path root =
                extractionDirectory
                        .toAbsolutePath()
                        .normalize();

        int entryCount = 0;
        int fileCount = 0;
        long totalExtractedBytes = 0;

        try {

            Files.createDirectories(root);

            try (
                    InputStream fileInput =
                            Files.newInputStream(archive);

                    BufferedInputStream bufferedInput =
                            new BufferedInputStream(fileInput);

                    ZipInputStream zipInput =
                            new ZipInputStream(bufferedInput)
            ) {

                ZipEntry entry;

                while ((entry = zipInput.getNextEntry()) != null) {

                    entryCount++;

                    if (entryCount > properties.maxEntries()) {
                        throw new InvalidUploadException(
                                "ZIP contains too many entries"
                        );
                    }

                    Path target =
                            resolveSafePath(root, entry.getName());

                    if (entry.isDirectory()) {

                        Files.createDirectories(target);
                        zipInput.closeEntry();
                        continue;
                    }

                    validateAllowedFile(target);

                    if (Files.exists(target)) {
                        throw new InvalidUploadException(
                                "ZIP contains duplicate file paths"
                        );
                    }

                    Path parent = target.getParent();

                    if (parent != null) {
                        Files.createDirectories(parent);
                    }

                    long extractedBytes =
                            copyEntryWithLimits(
                                    zipInput,
                                    target,
                                    totalExtractedBytes
                            );

                    totalExtractedBytes += extractedBytes;
                    fileCount++;

                    zipInput.closeEntry();
                }
            }

            if (fileCount == 0) {
                throw new InvalidUploadException(
                        "ZIP does not contain any supported CV files"
                );
            }

            return new ZipExtractionResult(
                    fileCount,
                    totalExtractedBytes,
                    root
            );

        } catch (InvalidUploadException exception) {

            deleteQuietly(root);
            throw exception;

        } catch (ZipException exception) {

            deleteQuietly(root);

            throw new InvalidUploadException(
                    "Uploaded file is not a valid ZIP archive"
            );

        } catch (IOException exception) {

            deleteQuietly(root);

            throw new UploadStorageException(
                    "Failed to extract ZIP archive",
                    exception
            );
        }
    }

    private Path resolveSafePath(
            Path root,
            String entryName
    ) {

        if (entryName == null || entryName.isBlank()) {
            throw new InvalidUploadException(
                    "ZIP contains an invalid entry name"
            );
        }

        try {

            Path target =
                    root.resolve(entryName)
                            .normalize()
                            .toAbsolutePath();

            if (!target.startsWith(root)) {
                throw new InvalidUploadException(
                        "ZIP contains an unsafe path"
                );
            }

            return target;

        } catch (InvalidPathException exception) {

            throw new InvalidUploadException(
                    "ZIP contains an invalid path"
            );
        }
    }

    private void validateAllowedFile(Path path) {

        String filename =
                path.getFileName()
                        .toString()
                        .toLowerCase(Locale.ROOT);

        boolean supported =
                ALLOWED_EXTENSIONS.stream()
                        .anyMatch(filename::endsWith);

        if (!supported) {
            throw new InvalidUploadException(
                    "ZIP may contain only PDF and DOCX files"
            );
        }
    }

    private long copyEntryWithLimits(
            ZipInputStream zipInput,
            Path target,
            long alreadyExtracted
    ) throws IOException {

        long fileBytes = 0;

        long maxSingleFileBytes =
                properties.maxSingleFileSize().toBytes();

        long maxTotalBytes =
                properties.maxExtractedSize().toBytes();

        byte[] buffer = new byte[BUFFER_SIZE];

        try (
                OutputStream output =
                        Files.newOutputStream(
                                target,
                                StandardOpenOption.CREATE_NEW,
                                StandardOpenOption.WRITE
                        )
        ) {

            int read;

            while ((read = zipInput.read(buffer)) != -1) {

                fileBytes += read;

                if (fileBytes > maxSingleFileBytes) {
                    throw new InvalidUploadException(
                            "A CV exceeds the maximum allowed size"
                    );
                }

                if (alreadyExtracted + fileBytes > maxTotalBytes) {
                    throw new InvalidUploadException(
                            "ZIP expands beyond the maximum allowed size"
                    );
                }

                output.write(buffer, 0, read);
            }
        }

        return fileBytes;
    }

    private void deleteQuietly(Path root) {

        if (Files.notExists(root)) {
            return;
        }

        try (var paths = Files.walk(root)) {

            paths.sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            
                        }
                    });

        } catch (IOException ignored) {
            
        }
    }
}