package com.adil.cvscanner.processing.api;

import com.adil.cvscanner.processing.domain.ProcessingFailure;
import com.adil.cvscanner.processing.infrastructure.ProcessingFailureRepository;
import com.adil.cvscanner.security.SecurityTestUsers;
import com.adil.cvscanner.upload.domain.CvUpload;
import com.adil.cvscanner.upload.infrastructure.CvUploadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ProcessingFailureControllerIT {

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
                            "cvscanner_failure_api_test"
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

    @Autowired
    private ProcessingFailureRepository
            processingFailureRepository;

    /*
     * ============================================================
     * TEST CONFIG
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
                "app.batch.retry.delay",
                () -> "0ms"
        );
    }

    /*
     * ============================================================
     * CLEAN DATABASE
     * ============================================================
     */

    @BeforeEach
    void cleanDatabase() {

        /*
         * processing_failure.upload_id
         *
         * FK ilə cv_upload-a bağlıdır.
         *
         * Ona görə əvvəl child table,
         * sonra parent table silinir.
         */

        processingFailureRepository.deleteAll();

        cvUploadRepository.deleteAll();
    }

    /*
     * ============================================================
     * TEST 1
     * EXISTING UPLOAD + FAILURES
     * ============================================================
     */

    @Test
    void shouldReturnFailuresForExistingUploadNewestFirst()
            throws Exception {

        CvUpload upload =
                createUpload(
                        "broken-candidates.zip",
                        3
                );

        ProcessingFailure oldFailure =
                new ProcessingFailure(
                        upload,
                        "old-broken.pdf",
                        "EMPTY_DOCUMENT",
                        "Document does not contain extractable text"
                );

        processingFailureRepository.saveAndFlush(
                oldFailure
        );

        /*
         * createdAt dəyərlərinin fərqli
         * olmasını təmin edirik.
         */

        Thread.sleep(
                30
        );

        ProcessingFailure newestFailure =
                new ProcessingFailure(
                        upload,
                        "latest-broken.pdf",
                        "UNSUPPORTED_MEDIA_TYPE",
                        "Unsupported media type: text/plain"
                );

        processingFailureRepository.saveAndFlush(
                newestFailure
        );

        assertThat(
                processingFailureRepository.count()
        ).isEqualTo(
                2
        );

        mockMvc.perform(
                        recruiterGet(
                                "/api/v1/uploads/{uploadId}/failures",
                                upload.getId()
                        )
                )
                .andExpect(
                        status()
                                .isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.page"
                        ).value(
                                0
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.size"
                        ).value(
                                20
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.totalElements"
                        ).value(
                                2
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.totalPages"
                        ).value(
                                1
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.first"
                        ).value(
                                true
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.last"
                        ).value(
                                true
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.content[0].filename"
                        ).value(
                                "latest-broken.pdf"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.content[0].errorCode"
                        ).value(
                                "UNSUPPORTED_MEDIA_TYPE"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.content[0].errorMessage"
                        ).value(
                                "Unsupported media type: text/plain"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.content[0].createdAt"
                        ).isNotEmpty()
                )
                .andExpect(
                        jsonPath(
                                "$.content[1].filename"
                        ).value(
                                "old-broken.pdf"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.content[1].errorCode"
                        ).value(
                                "EMPTY_DOCUMENT"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.content[1].errorMessage"
                        ).value(
                                "Document does not contain extractable text"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.content[1].createdAt"
                        ).isNotEmpty()
                );
    }

    /*
     * ============================================================
     * TEST 2
     * PAGINATION
     * ============================================================
     */

    @Test
    void shouldPaginateProcessingFailures()
            throws Exception {

        CvUpload upload =
                createUpload(
                        "pagination.zip",
                        5
                );

        saveFailure(
                upload,
                "failure-1.pdf",
                "PARSE_FAILED"
        );

        Thread.sleep(
                10
        );

        saveFailure(
                upload,
                "failure-2.pdf",
                "PARSE_FAILED"
        );

        Thread.sleep(
                10
        );

        saveFailure(
                upload,
                "failure-3.pdf",
                "PARSE_FAILED"
        );

        Thread.sleep(
                10
        );

        saveFailure(
                upload,
                "failure-4.pdf",
                "PARSE_FAILED"
        );

        Thread.sleep(
                10
        );

        saveFailure(
                upload,
                "failure-5.pdf",
                "PARSE_FAILED"
        );

        mockMvc.perform(
                        recruiterGet(
                                "/api/v1/uploads/{uploadId}/failures",
                                upload.getId()
                        )
                                .param(
                                        "page",
                                        "1"
                                )
                                .param(
                                        "size",
                                        "2"
                                )
                )
                .andExpect(
                        status()
                                .isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.page"
                        ).value(
                                1
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.size"
                        ).value(
                                2
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.totalElements"
                        ).value(
                                5
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.totalPages"
                        ).value(
                                3
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.first"
                        ).value(
                                false
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.last"
                        ).value(
                                false
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.content.length()"
                        ).value(
                                2
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.content[0].filename"
                        ).value(
                                "failure-3.pdf"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.content[1].filename"
                        ).value(
                                "failure-2.pdf"
                        )
                );
    }

    /*
     * ============================================================
     * TEST 3
     * EXISTING UPLOAD + ZERO FAILURES
     * ============================================================
     */

    @Test
    void shouldReturnEmptyPageWhenUploadHasNoFailures()
            throws Exception {

        CvUpload upload =
                createUpload(
                        "all-valid.zip",
                        4
                );

        assertThat(
                processingFailureRepository.count()
        ).isZero();

        mockMvc.perform(
                        recruiterGet(
                                "/api/v1/uploads/{uploadId}/failures",
                                upload.getId()
                        )
                )
                .andExpect(
                        status()
                                .isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.content"
                        ).isArray()
                )
                .andExpect(
                        jsonPath(
                                "$.content"
                        ).isEmpty()
                )
                .andExpect(
                        jsonPath(
                                "$.page"
                        ).value(
                                0
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.size"
                        ).value(
                                20
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.totalElements"
                        ).value(
                                0
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.totalPages"
                        ).value(
                                0
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.first"
                        ).value(
                                true
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.last"
                        ).value(
                                true
                        )
                );
    }

    /*
     * ============================================================
     * TEST 4
     * UNKNOWN UPLOAD
     * ============================================================
     */

    @Test
    void shouldReturn404WhenUploadDoesNotExist()
            throws Exception {

        UUID unknownUploadId =
                UUID.randomUUID();

        assertThat(
                cvUploadRepository.existsById(
                        unknownUploadId
                )
        ).isFalse();

        mockMvc.perform(
                        recruiterGet(
                                "/api/v1/uploads/{uploadId}/failures",
                                unknownUploadId
                        )
                )
                .andExpect(
                        status()
                                .isNotFound()
                );
    }

    /*
     * ============================================================
     * TEST 5
     * NEGATIVE PAGE
     * ============================================================
     */

    @Test
    void shouldReturn400WhenPageIsNegative()
            throws Exception {

        CvUpload upload =
                createUpload(
                        "negative-page.zip",
                        1
                );

        mockMvc.perform(
                        recruiterGet(
                                "/api/v1/uploads/{uploadId}/failures",
                                upload.getId()
                        )
                                .param(
                                        "page",
                                        "-1"
                                )
                )
                .andExpect(
                        status()
                                .isBadRequest()
                );
    }

    /*
     * ============================================================
     * TEST 6
     * PAGE SIZE TOO LARGE
     * ============================================================
     */

    @Test
    void shouldReturn400WhenPageSizeExceedsMaximum()
            throws Exception {

        CvUpload upload =
                createUpload(
                        "too-large-page.zip",
                        1
                );

        mockMvc.perform(
                        recruiterGet(
                                "/api/v1/uploads/{uploadId}/failures",
                                upload.getId()
                        )
                                .param(
                                        "size",
                                        "101"
                                )
                )
                .andExpect(
                        status()
                                .isBadRequest()
                );
    }

    /*
     * ============================================================
     * AUTHENTICATED GET
     * ============================================================
     *
     * Processing failure endpoint final RBAC
     * modelində RECRUITER və ADMIN üçündür.
     *
     * Bu business/controller IT-lərdə
     * RECRUITER context istifadə edirik.
     */

    private MockHttpServletRequestBuilder recruiterGet(
            String url,
            Object... uriVariables
    ) {

        return get(
                url,
                uriVariables
        )
                .with(
                        SecurityTestUsers.recruiter()
                );
    }

    /*
     * ============================================================
     * UPLOAD FACTORY
     * ============================================================
     */

    private CvUpload createUpload(
            String filename,
            int totalFiles
    ) {

        CvUpload upload =
                new CvUpload(
                        filename
                );

        upload.registerDiscoveredFiles(
                totalFiles
        );

        return cvUploadRepository.saveAndFlush(
                upload
        );
    }

    /*
     * ============================================================
     * FAILURE FACTORY
     * ============================================================
     */

    private ProcessingFailure saveFailure(
            CvUpload upload,
            String filename,
            String errorCode
    ) {

        ProcessingFailure failure =
                new ProcessingFailure(
                        upload,
                        filename,
                        errorCode,
                        "Simulated processing failure for "
                                + filename
                );

        return processingFailureRepository
                .saveAndFlush(
                        failure
                );
    }

    /*
     * ============================================================
     * TEMP STORAGE ROOT
     * ============================================================
     */

    private static Path createStorageRoot() {

        try {

            return Files.createTempDirectory(
                    "cvscanner-processing-failure-it-"
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