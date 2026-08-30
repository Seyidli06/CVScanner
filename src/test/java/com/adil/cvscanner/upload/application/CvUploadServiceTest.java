package com.adil.cvscanner.upload.application;

import com.adil.cvscanner.upload.domain.CvUpload;
import com.adil.cvscanner.upload.infrastructure.CvUploadRepository;
import com.adil.cvscanner.upload.infrastructure.LocalUploadStorage;
import com.adil.cvscanner.upload.infrastructure.SafeZipExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CvUploadServiceTest {

    @Mock
    private CvUploadRepository uploadRepository;

    @Mock
    private LocalUploadStorage uploadStorage;

    @Mock
    private SafeZipExtractor zipExtractor;

    private CvUploadService cvUploadService;

    @BeforeEach
    void setUp() {

        cvUploadService =
                new CvUploadService(
                        uploadRepository,
                        uploadStorage,
                        zipExtractor
                );
    }

    @Test
    void shouldPreserveSuccessfulUploadWhenStagingCleanupFails() {

        MockMultipartFile file =
                createMultipartFile();

        Path stagedFile =
                Path.of(
                        "storage",
                        "staging",
                        "test-staging-id",
                        "upload.zip"
                );

        Path uploadDirectory =
                Path.of(
                        "storage",
                        "uploads",
                        "test-upload"
                );

        Path extractionDirectory =
                uploadDirectory.resolve(
                        "cvs"
                );

        Path stagingDirectory =
                stagedFile.getParent();

        when(
                uploadStorage.stage(file)
        ).thenReturn(
                stagedFile
        );

        when(
                uploadStorage.uploadDirectory(
                        any(UUID.class)
                )
        ).thenReturn(
                uploadDirectory
        );

        when(
                uploadStorage.extractionDirectory(
                        any(UUID.class)
                )
        ).thenReturn(
                extractionDirectory
        );

        when(
                zipExtractor.extract(
                        stagedFile,
                        extractionDirectory
                )
        ).thenReturn(
                new ZipExtractionResult(
                        2,
                        1024L,
                        extractionDirectory
                )
        );

        when(
                uploadRepository.saveAndFlush(
                        any(CvUpload.class)
                )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );

        RuntimeException stagingCleanupFailure =
                new RuntimeException(
                        "Simulated staging cleanup failure"
                );

        doThrow(
                stagingCleanupFailure
        )
                .when(
                        uploadStorage
                )
                .deleteRecursively(
                        stagingDirectory
                );

        CvUpload result =
                cvUploadService.upload(
                        file
                );

        assertThat(
                result
        ).isNotNull();

        assertThat(
                result.getOriginalFilename()
        ).isEqualTo(
                "candidates.zip"
        );

        assertThat(
                result.getTotalFiles()
        ).isEqualTo(
                2
        );

        verify(
                uploadRepository
        ).saveAndFlush(
                result
        );

        verify(
                uploadStorage
        ).deleteRecursively(
                stagingDirectory
        );
    }

    @Test
    void shouldPreservePrimaryExceptionWhenUploadCleanupAlsoFails() {

        MockMultipartFile file =
                createMultipartFile();

        Path stagedFile =
                Path.of(
                        "storage",
                        "staging",
                        "test-staging-id",
                        "upload.zip"
                );

        Path uploadDirectory =
                Path.of(
                        "storage",
                        "uploads",
                        "test-upload"
                );

        Path extractionDirectory =
                uploadDirectory.resolve(
                        "cvs"
                );

        when(
                uploadStorage.stage(file)
        ).thenReturn(
                stagedFile
        );

        when(
                uploadStorage.uploadDirectory(
                        any(UUID.class)
                )
        ).thenReturn(
                uploadDirectory
        );

        when(
                uploadStorage.extractionDirectory(
                        any(UUID.class)
                )
        ).thenReturn(
                extractionDirectory
        );

        RuntimeException processingFailure =
                new RuntimeException(
                        "Simulated extraction failure"
                );

        RuntimeException uploadCleanupFailure =
                new RuntimeException(
                        "Simulated upload cleanup failure"
                );

        when(
                zipExtractor.extract(
                        stagedFile,
                        extractionDirectory
                )
        ).thenThrow(
                processingFailure
        );

        doThrow(
                uploadCleanupFailure
        )
                .when(
                        uploadStorage
                )
                .deleteRecursively(
                        uploadDirectory
                );

        RuntimeException thrown =
                catchThrowableOfType(
                        () ->
                                cvUploadService.upload(
                                        file
                                ),
                        RuntimeException.class
                );

        assertThat(
                thrown
        ).isSameAs(
                processingFailure
        );

        assertThat(
                thrown.getSuppressed()
        )
                .containsExactly(
                        uploadCleanupFailure
                );

        verify(
                uploadRepository,
                never()
        ).saveAndFlush(
                any(CvUpload.class)
        );

        verify(
                uploadStorage
        ).deleteRecursively(
                uploadDirectory
        );

        verify(
                uploadStorage
        ).deleteRecursively(
                stagedFile.getParent()
        );
    }

    private MockMultipartFile createMultipartFile() {

        return new MockMultipartFile(
                "file",
                "candidates.zip",
                "application/zip",
                new byte[]{
                        1,
                        2,
                        3
                }
        );
    }
}