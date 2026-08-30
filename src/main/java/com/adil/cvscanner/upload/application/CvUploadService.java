package com.adil.cvscanner.upload.application;

import com.adil.cvscanner.upload.domain.CvUpload;
import com.adil.cvscanner.upload.infrastructure.CvUploadRepository;
import com.adil.cvscanner.upload.infrastructure.LocalUploadStorage;
import com.adil.cvscanner.upload.infrastructure.SafeZipExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

@Service
public class CvUploadService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(CvUploadService.class);

    private final CvUploadRepository uploadRepository;
    private final LocalUploadStorage uploadStorage;
    private final SafeZipExtractor zipExtractor;

    public CvUploadService(
            CvUploadRepository uploadRepository,
            LocalUploadStorage uploadStorage,
            SafeZipExtractor zipExtractor
    ) {
        this.uploadRepository = uploadRepository;
        this.uploadStorage = uploadStorage;
        this.zipExtractor = zipExtractor;
    }

    public CvUpload upload(MultipartFile file) {

        validateBasicRequest(file);

        Path stagedFile =
                uploadStorage.stage(file);

        CvUpload upload =
                new CvUpload(file.getOriginalFilename());

        Path uploadDirectory =
                uploadStorage.uploadDirectory(
                        upload.getId()
                );

        try {

            Path extractionDirectory =
                    uploadStorage.extractionDirectory(
                            upload.getId()
                    );

            ZipExtractionResult extraction =
                    zipExtractor.extract(
                            stagedFile,
                            extractionDirectory
                    );

            upload.registerDiscoveredFiles(
                    extraction.fileCount()
            );

            return uploadRepository.saveAndFlush(
                    upload
            );

        } catch (RuntimeException exception) {

            cleanupFailedUploadStorage(
                    uploadDirectory,
                    upload.getId(),
                    exception
            );

            throw exception;

        } finally {

            cleanupStagingStorage(
                    stagedFile
            );
        }
    }

    private void cleanupFailedUploadStorage(
            Path uploadDirectory,
            UUID uploadId,
            RuntimeException primaryException
    ) {

        try {

            uploadStorage.deleteRecursively(
                    uploadDirectory
            );

        } catch (RuntimeException cleanupException) {

            primaryException.addSuppressed(
                    cleanupException
            );

            LOGGER.warn(
                    "UPLOAD_STORAGE_COMPENSATION_FAILED uploadId={} errorType={}",
                    uploadId,
                    cleanupException
                            .getClass()
                            .getSimpleName()
            );
        }
    }

    private void cleanupStagingStorage(
            Path stagedFile
    ) {

        if (stagedFile == null) {
            return;
        }

        Path stagingDirectory =
                stagedFile.getParent();

        if (stagingDirectory == null) {
            return;
        }

        try {

            uploadStorage.deleteRecursively(
                    stagingDirectory
            );

        } catch (RuntimeException cleanupException) {

            LOGGER.warn(
                    "STAGING_STORAGE_CLEANUP_FAILED errorType={}",
                    cleanupException
                            .getClass()
                            .getSimpleName()
            );
        }
    }

    private void validateBasicRequest(
            MultipartFile file
    ) {

        if (file == null || file.isEmpty()) {

            throw new InvalidUploadException(
                    "Uploaded file must not be empty"
            );
        }

        String filename =
                file.getOriginalFilename();

        if (
                filename == null
                        ||
                        !filename
                                .toLowerCase(Locale.ROOT)
                                .endsWith(".zip")
        ) {

            throw new InvalidUploadException(
                    "Only ZIP archives are supported"
            );
        }
    }
}