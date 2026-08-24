package com.adil.cvscanner.common.api;

import com.adil.cvscanner.processing.application.CvProcessingJobLauncher;
import com.adil.cvscanner.processing.application.CvProcessingLaunchException;
import com.adil.cvscanner.security.SecurityTestUsers;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ApiErrorContractIT {

    /*
     * ============================================================
     * TEMP STORAGE
     * ============================================================
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
                            "cvscanner_error_contract_test"
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
     * Upload launch-failure error contract üçün
     * yalnız Batch launcher mock olunur.
     */

    @MockitoBean
    private CvProcessingJobLauncher cvProcessingJobLauncher;

    /*
     * ============================================================
     * TEST PROPERTIES
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

        /*
         * Default Spring Boot error response
         * internal məlumat expose etməsin.
         */

        registry.add(
                "server.error.include-message",
                () -> "never"
        );

        registry.add(
                "server.error.include-stacktrace",
                () -> "never"
        );

        registry.add(
                "server.error.include-binding-errors",
                () -> "never"
        );
    }

    /*
     * ============================================================
     * CLEAN
     * ============================================================
     */

    @BeforeEach
    void setUp() {

        reset(
                cvProcessingJobLauncher
        );

        cvUploadRepository.deleteAll();
    }

    /*
     * ============================================================
     * TEST 1
     * INVALID CANDIDATE BUSINESS QUERY
     * ============================================================
     */

    @Test
    void shouldReturnStandardErrorForInvalidCandidateQuery()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/v1/candidates"
                        )
                                .param(
                                        "size",
                                        "101"
                                )
                                .with(
                                        SecurityTestUsers.recruiter()
                                )
                )
                .andExpect(
                        status()
                                .isBadRequest()
                )
                .andExpect(
                        jsonPath(
                                "$.timestamp"
                        ).isNotEmpty()
                )
                .andExpect(
                        jsonPath(
                                "$.status"
                        ).value(
                                400
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.error"
                        ).value(
                                "Bad Request"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.code"
                        ).value(
                                "INVALID_CANDIDATE_QUERY"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.message"
                        ).value(
                                "size must not exceed 100"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.path"
                        ).value(
                                "/api/v1/candidates"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.trace"
                        ).doesNotExist()
                )
                .andExpect(
                        jsonPath(
                                "$.stackTrace"
                        ).doesNotExist()
                )
                .andExpect(
                        jsonPath(
                                "$.exception"
                        ).doesNotExist()
                )
                .andExpect(
                        jsonPath(
                                "$.cause"
                        ).doesNotExist()
                );
    }

    /*
     * ============================================================
     * TEST 2
     * INVALID REQUEST PARAMETER TYPE
     * ============================================================
     */

    @Test
    void shouldReturnStandardErrorForMalformedUuid()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/v1/uploads/not-a-valid-uuid"
                        )
                                .with(
                                        SecurityTestUsers.recruiter()
                                )
                )
                .andExpect(
                        status()
                                .isBadRequest()
                )
                .andExpect(
                        jsonPath(
                                "$.timestamp"
                        ).isNotEmpty()
                )
                .andExpect(
                        jsonPath(
                                "$.status"
                        ).value(
                                400
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.error"
                        ).value(
                                "Bad Request"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.code"
                        ).value(
                                "INVALID_REQUEST_PARAMETER"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.message"
                        ).value(
                                "Invalid value for parameter 'uploadId'"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.path"
                        ).value(
                                "/api/v1/uploads/not-a-valid-uuid"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.trace"
                        ).doesNotExist()
                )
                .andExpect(
                        jsonPath(
                                "$.exception"
                        ).doesNotExist()
                )
                .andExpect(
                        jsonPath(
                                "$.cause"
                        ).doesNotExist()
                );
    }

    /*
     * ============================================================
     * TEST 3
     * INVALID ENUM PARAMETER
     * ============================================================
     */

    @Test
    void shouldReturnStandardErrorForInvalidJobType()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/v1/candidates"
                        )
                                .param(
                                        "jobType",
                                        "SPACE"
                                )
                                .with(
                                        SecurityTestUsers.recruiter()
                                )
                )
                .andExpect(
                        status()
                                .isBadRequest()
                )
                .andExpect(
                        jsonPath(
                                "$.status"
                        ).value(
                                400
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.error"
                        ).value(
                                "Bad Request"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.code"
                        ).value(
                                "INVALID_REQUEST_PARAMETER"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.message"
                        ).value(
                                "Invalid value for parameter 'jobType'"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.path"
                        ).value(
                                "/api/v1/candidates"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.trace"
                        ).doesNotExist()
                );
    }

    /*
     * ============================================================
     * TEST 4
     * UNKNOWN UPLOAD
     * ============================================================
     */

    @Test
    void shouldReturnStandard404ForUnknownUpload()
            throws Exception {

        UUID unknownUploadId =
                UUID.randomUUID();

        mockMvc.perform(
                        get(
                                "/api/v1/uploads/{uploadId}",
                                unknownUploadId
                        )
                                .with(
                                        SecurityTestUsers.recruiter()
                                )
                )
                .andExpect(
                        status()
                                .isNotFound()
                )
                .andExpect(
                        jsonPath(
                                "$.timestamp"
                        ).isNotEmpty()
                )
                .andExpect(
                        jsonPath(
                                "$.status"
                        ).value(
                                404
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.error"
                        ).value(
                                "Not Found"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.code"
                        ).value(
                                "UPLOAD_NOT_FOUND"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.message"
                        ).value(
                                "CV upload not found: "
                                        + unknownUploadId
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.path"
                        ).value(
                                "/api/v1/uploads/"
                                        + unknownUploadId
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.trace"
                        ).doesNotExist()
                )
                .andExpect(
                        jsonPath(
                                "$.exception"
                        ).doesNotExist()
                );
    }

    /*
     * ============================================================
     * TEST 5
     * INVALID PROCESSING FAILURE QUERY
     * ============================================================
     */

    @Test
    void shouldReturnStandardErrorForInvalidFailureQuery()
            throws Exception {

        UUID uploadId =
                UUID.randomUUID();

        mockMvc.perform(
                        get(
                                "/api/v1/uploads/{uploadId}/failures",
                                uploadId
                        )
                                .param(
                                        "size",
                                        "101"
                                )
                                .with(
                                        SecurityTestUsers.recruiter()
                                )
                )
                .andExpect(
                        status()
                                .isBadRequest()
                )
                .andExpect(
                        jsonPath(
                                "$.timestamp"
                        ).isNotEmpty()
                )
                .andExpect(
                        jsonPath(
                                "$.status"
                        ).value(
                                400
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.error"
                        ).value(
                                "Bad Request"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.code"
                        ).value(
                                "INVALID_PROCESSING_FAILURE_QUERY"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.message"
                        ).value(
                                "size must not exceed 100"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.path"
                        ).value(
                                "/api/v1/uploads/"
                                        + uploadId
                                        + "/failures"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.trace"
                        ).doesNotExist()
                );
    }

    /*
     * ============================================================
     * TEST 6
     * INTERNAL BATCH LAUNCH FAILURE
     * ============================================================
     *
     * Internal exception:
     *
     * CvProcessingLaunchException
     *
     * client-ə implementation detail,
     * stack trace, Batch internals və
     * exception class çıxmamalıdır.
     */

    @Test
    void shouldHideInternalDetailsForProcessingLaunchFailure()
            throws Exception {

        CvProcessingLaunchException internalException =
                mock(
                        CvProcessingLaunchException.class
                );

        doThrow(
                internalException
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
                        "candidate.pdf",
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
                                .isInternalServerError()
                )
                .andExpect(
                        jsonPath(
                                "$.timestamp"
                        ).isNotEmpty()
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
                                "$.trace"
                        ).doesNotExist()
                )
                .andExpect(
                        jsonPath(
                                "$.stackTrace"
                        ).doesNotExist()
                )
                .andExpect(
                        jsonPath(
                                "$.exception"
                        ).doesNotExist()
                )
                .andExpect(
                        jsonPath(
                                "$.cause"
                        ).doesNotExist()
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
     * SMALL PDF CONTENT
     * ============================================================
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
     * STORAGE ROOT
     * ============================================================
     */

    private static Path createStorageRoot() {

        try {

            return Files.createTempDirectory(
                    "cvscanner-api-error-contract-it-"
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