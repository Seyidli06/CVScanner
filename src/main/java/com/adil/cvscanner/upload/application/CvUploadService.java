package com.adil.cvscanner.upload.application;

import com.adil.cvscanner.upload.domain.CvUpload;
import com.adil.cvscanner.upload.infrastructure.CvUploadRepository;
import com.adil.cvscanner.upload.infrastructure.LocalUploadStorage;
import com.adil.cvscanner.upload.infrastructure.SafeZipExtractor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

@Service
public class CvUploadService {

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

            return uploadRepository.saveAndFlush(upload);

        } catch (RuntimeException exception) {

            uploadStorage.deleteRecursively(
                    uploadDirectory
            );

            throw exception;

        } finally {

            Path stagingDirectory =
                    stagedFile.getParent();

            uploadStorage.deleteRecursively(
                    stagingDirectory
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
                filename == null ||
                        !filename
                                .toLowerCase()
                                .endsWith(".zip")
        ) {

            throw new InvalidUploadException(
                    "Only ZIP archives are supported"
            );
        }
    }
}