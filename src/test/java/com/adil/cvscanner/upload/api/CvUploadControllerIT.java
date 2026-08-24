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

    /*
     * ============================================================
     * TEMP STORAGE
     * ============================================================
     *
     * Real LocalUploadStorage istifadə olunur.
     *
     * Test heç vaxt production/dev storage-a toxunmur.
     */

    private static final Path STORAGE_ROOT =
            createStorageRoot();

    /*
     * ============================================================
     * REAL POSTGRESQL
     * ============================================================
     */

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

    /*
     * ============================================================
     * ONLY BATCH LAUNCH IS MOCKED
     * ============================================================
     *
     * Bunlar REAL qalır:
     *
     * Controller
     * WorkflowService
     * CvUploadService
     * LocalUploadStorage
     * SafeZipExtractor
     * Repository
     * PostgreSQL
     *
     * Yalnız Batch job-un arxa planda həqiqətən
     * başlamasını bu controller testində istəmirik.
     *
     * Batch özü ayrıca:
     *
     * CvProcessingJobIT
     * CvProcessingRetryIT
     * CvProcessingRestartIT
     *
     * ilə test olunur.
     */

    @MockitoBean
    private CvProcessingJobLauncher cvProcessingJobLauncher;

    /*
     * ============================================================
     * TEST CONFIGURATION
     * ============================================================
     */

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

    /*
     * ============================================================
     * CLEAN STATE
     * ============================================================
     */

    @BeforeEach
    void setUp() throws IOException {

        /*
         * Mockito mock hər testdən əvvəl
         * tam təmiz vəziyyətə gətirilir.
         */

        reset(
                cvProcessingJobLauncher
        );

        /*
         * Real PostgreSQL test datası təmizlənir.
         */

        cvUploadRepository.deleteAll();

        /*
         * Temp storage-da əvvəlki testdən
         * fayl qalmasın.
         */

        clearStorageRoot();
    }

    /*
     * ============================================================
     * TEST 1
     * VALID ZIP
     * ============================================================
     *
     * POST /api/v1/uploads
     *
     * ZIP:
     *
     * candidates.zip
     * └── john-doe.pdf
     *
     * Expected:
     *
     * HTTP 202
     *
     * upload DB-də yaranır
     * status = UPLOADED
     * totalFiles = 1
     *
     * Batch launcher çağırılır.
     */

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

        /*
         * =====================================================
         * ACT + HTTP ASSERT
         * =====================================================
         */

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

        /*
         * =====================================================
         * DATABASE ASSERT
         * =====================================================
         */

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

        /*
         * totalFiles HTTP response-da deyil.
         *
         * Amma business state-də həqiqətən
         * düzgün persist edildiyini burada
         * yoxlayırıq.
         */

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

        /*
         * Workflow Batch launcher-a qədər
         * gəlib.
         */

        verify(
                cvProcessingJobLauncher
        ).launch(
                upload.getId()
        );

        /*
         * ZIP real storage-a extract edilib.
         */

        assertThat(
                regularFileExists(
                        "john-doe.pdf"
                )
        ).isTrue();
    }

    /*
     * ============================================================
     * TEST 2
     * INVALID ZIP
     * ============================================================
     */

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

        /*
         * ZIP validation uğursuz olduğuna görə
         * Batch mərhələsinə çatmamalıdır.
         */

        org.mockito.Mockito.verifyNoInteractions(
                cvProcessingJobLauncher
        );
    }

    /*
     * ============================================================
     * TEST 3
     * ZIP SLIP ATTACK
     * ============================================================
     *
     * Archive daxilində:
     *
     * ../evil.pdf
     *
     * var.
     *
     * Bu path extraction directory-dən
     * kənara çıxmağa çalışır.
     *
     * Expected:
     *
     * HTTP 400
     * upload DB-də yoxdur
     * Batch başlamır
     * evil.pdf storage root-dan kənara yazılmır
     */

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

        /*
         * Əsas təhlükəsizlik assertion-u.
         *
         * evil.pdf heç yerdə storage daxilində
         * normal extracted file kimi yaranmamalıdır.
         */

        assertThat(
                regularFileExists(
                        "evil.pdf"
                )
        ).isFalse();
    }

    /*
     * ============================================================
     * TEST 4
     * IMMEDIATE BATCH LAUNCH FAILURE
     * ============================================================
     *
     * Flow:
     *
     * ZIP validation          SUCCESS
     * extraction              SUCCESS
     * DB save                 SUCCESS
     * Batch launch            FAILURE
     *
     * Expected:
     *
     * HTTP 500
     *
     * DB:
     * status = FAILED
     *
     * Extract olunmuş fayl saxlanılır.
     *
     * Bu vacibdir:
     *
     * launch failure zamanı storage-ı silmirik,
     * çünki debug/recovery/retry üçün fayl lazım ola bilər.
     */

    @Test
    void shouldReturn500AndMarkUploadFailedWhenJobLaunchFails()
            throws Exception {

        /*
         * Exception-in constructor signature-na
         * testimizi bağlamamaq üçün Mockito mock
         * exception istifadə edirik.
         *
         * Workflow üçün əsas məsələ type-dır:
         *
         * CvProcessingLaunchException
         */

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

        /*
         * =====================================================
         * HTTP ASSERT
         * =====================================================
         *
         * GlobalApiExceptionHandler contract.
         */

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

        /*
         * =====================================================
         * DATABASE ASSERT
         * =====================================================
         */

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

        /*
         * =====================================================
         * STORAGE ASSERT
         * =====================================================
         *
         * Launch uğursuz olsa belə extracted CV
         * storage-da saxlanmalıdır.
         */

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

    /*
     * ============================================================
     * ZIP HELPER
     * ============================================================
     */

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

    /*
     * ============================================================
     * MINIMAL PDF-LIKE CONTENT
     * ============================================================
     *
     * Bu testdə Batch/Tika işləmir.
     *
     * Məqsəd ZIP upload/extraction flow-dur.
     *
     * Yenə də plain random bytes əvəzinə
     * PDF signature ilə başlayan content veririk.
     */

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

    /*
     * ============================================================
     * STORAGE SEARCH
     * ============================================================
     */

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

    /*
     * ============================================================
     * STORAGE CLEANUP
     * ============================================================
     */

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

    /*
     * ============================================================
     * TEMP ROOT CREATION
     * ============================================================
     */

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