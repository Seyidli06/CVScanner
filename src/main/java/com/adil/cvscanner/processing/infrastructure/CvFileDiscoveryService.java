package com.adil.cvscanner.processing.infrastructure;

import com.adil.cvscanner.processing.application.CvFileDiscoveryException;
import com.adil.cvscanner.upload.infrastructure.LocalUploadStorage;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
public class CvFileDiscoveryService {

    private final LocalUploadStorage uploadStorage;

    public CvFileDiscoveryService(
            LocalUploadStorage uploadStorage
    ) {
        this.uploadStorage = uploadStorage;
    }

    public List<Path> findCvFiles(UUID uploadId) {

        Path extractionDirectory =
                uploadStorage.extractionDirectory(
                        uploadId
                );

        if (
                Files.notExists(extractionDirectory)
                        || !Files.isDirectory(
                        extractionDirectory
                )
        ) {
            throw new CvFileDiscoveryException(
                    "CV extraction directory does not exist for upload: "
                            + uploadId
            );
        }

        try (
                var paths =
                        Files.walk(
                                extractionDirectory
                        )
        ) {

            List<Path> files =
                    paths
                            .filter(
                                    Files::isRegularFile
                            )
                            .filter(
                                    this::isSupportedCv
                            )
                            .sorted(
                                    (left, right) ->
                                            extractionDirectory
                                                    .relativize(left)
                                                    .toString()
                                                    .compareTo(
                                                            extractionDirectory
                                                                    .relativize(right)
                                                                    .toString()
                                                    )
                            )
                            .toList();

            if (files.isEmpty()) {
                throw new CvFileDiscoveryException(
                        "No CV files found for upload: "
                                + uploadId
                );
            }

            return files;

        } catch (IOException exception) {

            throw new CvFileDiscoveryException(
                    "Failed to discover CV files for upload: "
                            + uploadId,
                    exception
            );
        }
    }

    private boolean isSupportedCv(Path path) {

        String filename =
                path.getFileName()
                        .toString()
                        .toLowerCase(
                                Locale.ROOT
                        );

        return filename.endsWith(".pdf")
                || filename.endsWith(".docx");
    }
}