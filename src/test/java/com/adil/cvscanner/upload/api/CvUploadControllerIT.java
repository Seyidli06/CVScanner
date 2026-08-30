package com.adil.cvscanner.upload.api;

import com.adil.cvscanner.processing.application.CvProcessingLaunchException;
import com.adil.cvscanner.processing.application.CvProcessingJobLauncher;
import com.adil.cvscanner.security.SecurityTestUsers;
import com.adil.cvscanner.upload.domain.CvUpload;
import com.adil.cvscanner.upload.domain.UploadStatus;
import com.adil.cvscanner.upload.infrastructure.CvUploadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CvUploadControllerIT {

    private static final Path STORAGE_ROOT =
            createStorageRoot();

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    "postgres:16-alpine"
            )
                    .withDatabaseName(
                            "cvscanner_upload_test"
                    )
                    .withUsername(
                            "test"
                    )
                    .withPassword(
                            "test"
                    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CvUploadRepository cvUploadRepository;

    @MockitoBean
    private CvProcessingJobLauncher cvProcessingJobLauncher;

    @DynamicPropertySource
    static void properties(
            DynamicPropertyRegistry registry
    ) {

        registry.add(
                "app.upload.storage-root",
                () -> STORAGE_ROOT.toString()
        );

        registry.add(
                "app.upload.max-entries",
                () -> "100"
        );

        registry.add(
                "app.upload.max-extracted-size",
                () -> "100MB"
        );

        registry.add(
                "app.upload.max-single-file-size",
                () -> "10MB"
        );

        registry.add(
                "app.parsing.max-text-length",
                () -> "1000000"
        );

        registry.add(
                "app.batch.core-pool-size",
                () -> "2"
        );

        registry.add(
                "app.batch.max-pool-size",
                () -> "4"
        );

        registry.add(
                "app.batch.queue-capacity",
                () -> "100"
        );

        registry.add(
                "app.batch.await-termination-seconds",
                () -> "30"
        );

        registry.add(
                "app.batch.retry.max-retries",
                () -> "2"
        );

        registry.add(
                "app.batch.retry.delay",
                () -> "0ms"
        );
    }

    @BeforeEach
    void setUp() throws IOException {

        reset(
                cvProcessingJobLauncher
        );

        cvUploadRepository.deleteAll();

        clearStorageRoot();
    }

    @Test
    void shouldAcceptValidZipAndReturn202()
            throws Exception {

        byte[] zipBytes =
                createZip(
                        "john-doe.pdf",
                        minimalPdfBytes()
                );

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "candidates.zip",
                        "application/zip",
                        zipBytes
                );

        mockMvc.perform(
                        multipart(
                                "/api/v1/uploads"
                        )
                                .file(
                                        file
                                )
                                .with(
                                        SecurityTestUsers.recruiter()
                                )
                )
                .andExpect(
                        status()
                                .isAccepted()
                )
                .andExpect(
                        jsonPath(
                                "$.uploadId"
                        ).isNotEmpty()
                )
                .andExpect(
                        jsonPath(
                                "$.filename"
                        ).value(
                                "candidates.zip"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.status"
                        ).value(
                                "UPLOADED"
                        )
                );

        List<CvUpload> uploads =
                cvUploadRepository.findAll();

        assertThat(
                uploads
        ).hasSize(
                1
        );

        CvUpload upload =
                uploads.getFirst();

        assertThat(
                upload.getOriginalFilename()
        ).isEqualTo(
                "candidates.zip"
        );

        assertThat(
                upload.getStatus()
        ).isEqualTo(
                UploadStatus.UPLOADED
        );

        assertThat(
                upload.getTotalFiles()
        ).isEqualTo(
                1
        );

        assertThat(
                upload.getProcessedFiles()
        ).isZero();

        assertThat(
                upload.getFailedFiles()
        ).isZero();

        verify(
                cvProcessingJobLauncher
        ).launch(
                upload.getId()
        );

        assertThat(
                regularFileExists(
                        "john-doe.pdf"
                )
        ).isTrue();
    }

    @Test
    void shouldRejectInvalidZipWith400()
            throws Exception {

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "broken.zip",
                        "application/zip",
                        "this-is-not-a-valid-zip"
                                .getBytes(
                                        StandardCharsets.UTF_8
                                )
                );

        mockMvc.perform(
                        multipart(
                                "/api/v1/uploads"
                        )
                                .file(
                                        file
                                )
                                .with(
                                        SecurityTestUsers.recruiter()
                                )
                )
                .andExpect(
                        status()
                                .isBadRequest()
                );

        assertThat(
                cvUploadRepository.count()
        ).isZero();

        org.mockito.Mockito.verifyNoInteractions(
                cvProcessingJobLauncher
        );
    }

    @Test
    void shouldRejectZipSlipArchiveWith400()
            throws Exception {

        byte[] zipBytes =
                createZip(
                        "../evil.pdf",
                        minimalPdfBytes()
                );

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "zip-slip.zip",
                        "application/zip",
                        zipBytes
                );

        mockMvc.perform(
                        multipart(
                                "/api/v1/uploads"
                        )
                                .file(
                                        file
                                )
                                .with(
                                        SecurityTestUsers.recruiter()
                                )
                )
                .andExpect(
                        status()
                                .isBadRequest()
                );

        assertThat(
                cvUploadRepository.count()
        ).isZero();

        org.mockito.Mockito.verifyNoInteractions(
                cvProcessingJobLauncher
        );

        assertThat(
                regularFileExists(
                        "evil.pdf"
                )
        ).isFalse();
    }

    @Test
    void shouldReturn500AndMarkUploadFailedWhenJobLaunchFails()
            throws Exception {

        CvProcessingLaunchException launchException =
                mock(
                        CvProcessingLaunchException.class
                );

        doThrow(
                launchException
        )
                .when(
                        cvProcessingJobLauncher
                )
                .launch(
                        any(
                                UUID.class
                        )
                );

        byte[] zipBytes =
                createZip(
                        "launch-failure.pdf",
                        minimalPdfBytes()
                );

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "launch-failure.zip",
                        "application/zip",
                        zipBytes
                );

        mockMvc.perform(
                        multipart(
                                "/api/v1/uploads"
                        )
                                .file(
                                        file
                                )
                                .with(
                                        SecurityTestUsers.recruiter()
                                )
                )
                .andExpect(
                        status()
                                .isInternalServerError()
                )
                .andExpect(
                        jsonPath(
                                "$.status"
                        ).value(
                                500
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.error"
                        ).value(
                                "Internal Server Error"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.code"
                        ).value(
                                "PROCESSING_LAUNCH_FAILED"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.message"
                        ).value(
                                "Unable to start CV processing"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.path"
                        ).value(
                                "/api/v1/uploads"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.timestamp"
                        ).isNotEmpty()
                );

        List<CvUpload> uploads =
                cvUploadRepository.findAll();

        assertThat(
                uploads
        ).hasSize(
                1
        );

        CvUpload failedUpload =
                uploads.getFirst();

        assertThat(
                failedUpload.getOriginalFilename()
        ).isEqualTo(
                "launch-failure.zip"
        );

        assertThat(
                failedUpload.getStatus()
        ).isEqualTo(
                UploadStatus.FAILED
        );

        assertThat(
                failedUpload.getTotalFiles()
        ).isEqualTo(
                1
        );

        assertThat(
                failedUpload.getProcessedFiles()
        ).isZero();

        assertThat(
                failedUpload.getFailedFiles()
        ).isZero();

        assertThat(
                failedUpload.getCompletedAt()
        ).isNotNull();

        assertThat(
                regularFileExists(
                        "launch-failure.pdf"
                )
        ).isTrue();

        verify(
                cvProcessingJobLauncher
        ).launch(
                failedUpload.getId()
        );
    }

    private static byte[] createZip(
            String entryName,
            byte[] content
    ) throws IOException {

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        try (
                ZipOutputStream zipOutput =
                        new ZipOutputStream(
                                output
                        )
        ) {

            ZipEntry entry =
                    new ZipEntry(
                            entryName
                    );

            zipOutput.putNextEntry(
                    entry
            );

            zipOutput.write(
                    content
            );

            zipOutput.closeEntry();
        }

        return output.toByteArray();
    }

    private static byte[] minimalPdfBytes() {

        String content = """
                %PDF-1.4
                1 0 obj
                <<
                /Type /Catalog
                >>
                endobj
                trailer
                <<
                /Root 1 0 R
                >>
                %%EOF
                """;

        return content.getBytes(
                StandardCharsets.US_ASCII
        );
    }

    private static boolean regularFileExists(
            String filename
    ) throws IOException {

        try (
                var paths =
                        Files.walk(
                                STORAGE_ROOT
                        )
        ) {

            return paths
                    .filter(
                            Files::isRegularFile
                    )
                    .anyMatch(
                            path ->
                                    path.getFileName() != null
                                            &&
                                            filename.equals(
                                                    path
                                                            .getFileName()
                                                            .toString()
                                            )
                    );
        }
    }

    private static void clearStorageRoot()
            throws IOException {

        if (
                Files.notExists(
                        STORAGE_ROOT
                )
        ) {

            Files.createDirectories(
                    STORAGE_ROOT
            );

            return;
        }

        try (
                var paths =
                        Files.walk(
                                STORAGE_ROOT
                        )
        ) {

            paths
                    .sorted(
                            Comparator.reverseOrder()
                    )
                    .filter(
                            path ->
                                    !path.equals(
                                            STORAGE_ROOT
                                    )
                    )
                    .forEach(
                            CvUploadControllerIT::deletePath
                    );
        }
    }

    private static void deletePath(
            Path path
    ) {

        try {

            Files.deleteIfExists(
                    path
            );

        } catch (
                IOException exception
        ) {

            throw new UncheckedIOException(
                    exception
            );
        }
    }

    private static Path createStorageRoot() {

        try {

            return Files.createTempDirectory(
                    "cvscanner-upload-it-"
            );

        } catch (
                IOException exception
        ) {

            throw new ExceptionInInitializerError(
                    exception
            );
        }
    }
}
