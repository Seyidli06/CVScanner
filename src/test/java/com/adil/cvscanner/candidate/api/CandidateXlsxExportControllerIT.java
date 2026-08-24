package com.adil.cvscanner.candidate.api;

import com.adil.cvscanner.candidate.domain.Candidate;
import com.adil.cvscanner.candidate.domain.JobType;
import com.adil.cvscanner.candidate.infrastructure.CandidateRepository;
import com.adil.cvscanner.security.SecurityTestUsers;
import com.adil.cvscanner.upload.domain.CvUpload;
import com.adil.cvscanner.upload.infrastructure.CvUploadRepository;
import jakarta.persistence.EntityManagerFactory;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(
        properties = {
                "spring.jpa.properties.hibernate.generate_statistics=true"
        }
)
class CandidateXlsxExportControllerIT {

    /*
     * ============================================================
     * CONSTANTS
     * ============================================================
     */

    private static final String EXPORT_URL =
            "/api/v1/candidates/export.xlsx";

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

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
                            "cvscanner_xlsx_export_test"
                    )
                    .withUsername(
                            "test"
                    )
                    .withPassword(
                            "test"
                    );

    /*
     * ============================================================
     * DEPENDENCIES
     * ============================================================
     */

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired
    private CvUploadRepository cvUploadRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    /*
     * ============================================================
     * TEST DATA
     * ============================================================
     */

    private CvUpload backendUpload;

    private CvUpload secondUpload;

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
                "app.batch.retry.delay",
                () -> "0ms"
        );
    }

    /*
     * ============================================================
     * DEFAULT DATASET
     * ============================================================
     *
     * backendUpload:
     *
     * Alice
     * 5 years
     * Baku
     * REMOTE
     * Java, Spring Boot, PostgreSQL
     *
     * Bob
     * 2 years
     * Baku
     * ONSITE
     * Java, Docker
     *
     * Charlie
     * 8 years
     * Ganja
     * REMOTE
     * Python, FastAPI, PostgreSQL
     *
     * David
     * 4 years
     * Baku
     * HYBRID
     * JavaScript, React, Node.js
     *
     * secondUpload:
     *
     * Elvin
     * 6 years
     * Baku
     * REMOTE
     * Java, Redis
     *
     * Farid
     * 10 years
     * Sumgait
     * REMOTE
     * Java, Spring Boot
     */

    @BeforeEach
    void setUp() {

        /*
         * Candidate upload-a FK ilə bağlıdır.
         *
         * Ona görə əvvəl candidate-lər,
         * sonra upload-lar silinir.
         */

        candidateRepository.deleteAll();

        cvUploadRepository.deleteAll();

        /*
         * ========================================================
         * UPLOAD #1
         * ========================================================
         */

        backendUpload =
                new CvUpload(
                        "backend-candidates.zip"
                );

        backendUpload.registerDiscoveredFiles(
                4
        );

        backendUpload =
                cvUploadRepository.saveAndFlush(
                        backendUpload
                );

        /*
         * ========================================================
         * UPLOAD #2
         * ========================================================
         */

        secondUpload =
                new CvUpload(
                        "additional-candidates.zip"
                );

        secondUpload.registerDiscoveredFiles(
                2
        );

        secondUpload =
                cvUploadRepository.saveAndFlush(
                        secondUpload
                );

        /*
         * ========================================================
         * CANDIDATES
         * ========================================================
         */

        Candidate alice =
                candidate(
                        backendUpload,
                        "Alice Johnson",
                        5,
                        "Baku",
                        JobType.REMOTE,
                        "alice.pdf",
                        Set.of(
                                "Java",
                                "Spring Boot",
                                "PostgreSQL"
                        )
                );

        Candidate bob =
                candidate(
                        backendUpload,
                        "Bob Smith",
                        2,
                        "Baku",
                        JobType.ONSITE,
                        "bob.pdf",
                        Set.of(
                                "Java",
                                "Docker"
                        )
                );

        Candidate charlie =
                candidate(
                        backendUpload,
                        "Charlie Brown",
                        8,
                        "Ganja",
                        JobType.REMOTE,
                        "charlie.pdf",
                        Set.of(
                                "Python",
                                "FastAPI",
                                "PostgreSQL"
                        )
                );

        Candidate david =
                candidate(
                        backendUpload,
                        "David Wilson",
                        4,
                        "Baku",
                        JobType.HYBRID,
                        "david.pdf",
                        Set.of(
                                "JavaScript",
                                "React",
                                "Node.js"
                        )
                );

        Candidate elvin =
                candidate(
                        secondUpload,
                        "Elvin Aliyev",
                        6,
                        "Baku",
                        JobType.REMOTE,
                        "elvin.pdf",
                        Set.of(
                                "Java",
                                "Redis"
                        )
                );

        Candidate farid =
                candidate(
                        secondUpload,
                        "Farid Mammadov",
                        10,
                        "Sumgait",
                        JobType.REMOTE,
                        "farid.pdf",
                        Set.of(
                                "Java",
                                "Spring Boot"
                        )
                );

        candidateRepository.saveAllAndFlush(
                List.of(
                        alice,
                        bob,
                        charlie,
                        david,
                        elvin,
                        farid
                )
        );

        assertThat(
                candidateRepository.count()
        ).isEqualTo(
                6
        );
    }

    /*
     * ============================================================
     * TEST 1
     *
     * DOWNLOAD CONTRACT
     * +
     * XLSX WORKBOOK STRUCTURE
     * ============================================================
     */

    @Test
    void shouldDownloadValidXlsxWithExpectedWorkbookStructure()
            throws Exception {

        MvcResult result =
                performXlsxExport(
                        get(
                                EXPORT_URL
                        )
                );

        /*
         * ========================================================
         * HTTP RESPONSE
         * ========================================================
         */

        assertThat(
                result.getResponse()
                        .getStatus()
        ).isEqualTo(
                200
        );

        assertThat(
                result.getResponse()
                        .getHeader(
                                HttpHeaders.CONTENT_DISPOSITION
                        )
        ).isEqualTo(
                "attachment; filename=\"candidates.xlsx\""
        );

        assertThat(
                result.getResponse()
                        .getHeader(
                                HttpHeaders.CONTENT_TYPE
                        )
        ).isEqualTo(
                XLSX_CONTENT_TYPE
        );

        assertThat(
                result.getResponse()
                        .getHeader(
                                HttpHeaders.CACHE_CONTROL
                        )
        ).isEqualTo(
                "no-store"
        );

        assertThat(
                result.getResponse()
                        .getHeader(
                                "X-Content-Type-Options"
                        )
        ).isEqualTo(
                "nosniff"
        );

        assertThat(
                result.getResponse()
                        .getHeader(
                                "X-Correlation-ID"
                        )
        ).isNotBlank();

        /*
         * ========================================================
         * XLSX PARSE
         * ========================================================
         */

        try (
                XSSFWorkbook workbook =
                        workbook(
                                result
                        )
        ) {

            assertThat(
                    workbook.getNumberOfSheets()
            ).isEqualTo(
                    1
            );

            XSSFSheet sheet =
                    workbook.getSheet(
                            "Candidates"
                    );

            assertThat(
                    sheet
            ).isNotNull();

            /*
             * 1 header
             * +
             * 6 candidates
             */

            assertThat(
                    sheet.getPhysicalNumberOfRows()
            ).isEqualTo(
                    7
            );

            assertHeader(
                    sheet.getRow(
                            0
                    )
            );

            /*
             * Default sort:
             *
             * fullName ASC
             */

            assertThat(
                    stringCell(
                            sheet,
                            1,
                            2
                    )
            ).isEqualTo(
                    "Alice Johnson"
            );

            assertThat(
                    stringCell(
                            sheet,
                            2,
                            2
                    )
            ).isEqualTo(
                    "Bob Smith"
            );

            assertThat(
                    stringCell(
                            sheet,
                            3,
                            2
                    )
            ).isEqualTo(
                    "Charlie Brown"
            );

            assertThat(
                    stringCell(
                            sheet,
                            4,
                            2
                    )
            ).isEqualTo(
                    "David Wilson"
            );

            assertThat(
                    stringCell(
                            sheet,
                            5,
                            2
                    )
            ).isEqualTo(
                    "Elvin Aliyev"
            );

            assertThat(
                    stringCell(
                            sheet,
                            6,
                            2
                    )
            ).isEqualTo(
                    "Farid Mammadov"
            );

            /*
             * ====================================================
             * FREEZE PANE
             * ====================================================
             */

            assertThat(
                    sheet.getPaneInformation()
            ).isNotNull();

            assertThat(
                    sheet
                            .getPaneInformation()
                            .isFreezePane()
            ).isTrue();

            /*
             * ====================================================
             * AUTO FILTER
             * ====================================================
             *
             * A1:H1
             */

            assertThat(
                    sheet
                            .getCTWorksheet()
                            .isSetAutoFilter()
            ).isTrue();

            assertThat(
                    sheet
                            .getCTWorksheet()
                            .getAutoFilter()
                            .getRef()
            ).isEqualTo(
                    "A1:H1"
            );
        }
    }

    /*
     * ============================================================
     * TEST 2
     *
     * FILTERING
     * +
     * SORT
     * +
     * NUMERIC EXPERIENCE CELL
     * ============================================================
     */

    @Test
    void shouldApplyFiltersAndKeepExperienceNumeric()
            throws Exception {

        MvcResult result =
                performXlsxExport(
                        get(
                                EXPORT_URL
                        )
                                .param(
                                        "skill",
                                        "Java"
                                )
                                .param(
                                        "jobType",
                                        "REMOTE"
                                )
                                .param(
                                        "minExperience",
                                        "3"
                                )
                                .param(
                                        "sortBy",
                                        "yearsOfExperience"
                                )
                                .param(
                                        "direction",
                                        "desc"
                                )
                );

        try (
                XSSFWorkbook workbook =
                        workbook(
                                result
                        )
        ) {

            XSSFSheet sheet =
                    workbook.getSheet(
                            "Candidates"
                    );

            assertThat(
                    sheet
            ).isNotNull();

            /*
             * Header + 3 candidates.
             */

            assertThat(
                    sheet.getPhysicalNumberOfRows()
            ).isEqualTo(
                    4
            );

            assertThat(
                    stringCell(
                            sheet,
                            1,
                            2
                    )
            ).isEqualTo(
                    "Farid Mammadov"
            );

            assertThat(
                    stringCell(
                            sheet,
                            2,
                            2
                    )
            ).isEqualTo(
                    "Elvin Aliyev"
            );

            assertThat(
                    stringCell(
                            sheet,
                            3,
                            2
                    )
            ).isEqualTo(
                    "Alice Johnson"
            );

            /*
             * ====================================================
             * EXPERIENCE MUST BE NUMERIC
             * ====================================================
             */

            Cell faridExperience =
                    sheet
                            .getRow(
                                    1
                            )
                            .getCell(
                                    3
                            );

            assertThat(
                    faridExperience
            ).isNotNull();

            assertThat(
                    faridExperience
                            .getCellType()
            ).isEqualTo(
                    CellType.NUMERIC
            );

            assertThat(
                    faridExperience
                            .getNumericCellValue()
            ).isEqualTo(
                    10.0
            );

            Cell elvinExperience =
                    sheet
                            .getRow(
                                    2
                            )
                            .getCell(
                                    3
                            );

            assertThat(
                    elvinExperience
            ).isNotNull();

            assertThat(
                    elvinExperience
                            .getCellType()
            ).isEqualTo(
                    CellType.NUMERIC
            );

            assertThat(
                    elvinExperience
                            .getNumericCellValue()
            ).isEqualTo(
                    6.0
            );

            Cell aliceExperience =
                    sheet
                            .getRow(
                                    3
                            )
                            .getCell(
                                    3
                            );

            assertThat(
                    aliceExperience
            ).isNotNull();

            assertThat(
                    aliceExperience
                            .getCellType()
            ).isEqualTo(
                    CellType.NUMERIC
            );

            assertThat(
                    aliceExperience
                            .getNumericCellValue()
            ).isEqualTo(
                    5.0
            );
        }
    }

    /*
     * ============================================================
     * TEST 3
     *
     * UNICODE / AZERBAIJANI CHARACTERS
     * ============================================================
     */

    @Test
    void shouldPreserveUnicodeText()
            throws Exception {

        CvUpload unicodeUpload =
                new CvUpload(
                        "unicode-candidates.zip"
                );

        unicodeUpload.registerDiscoveredFiles(
                1
        );

        unicodeUpload =
                cvUploadRepository.saveAndFlush(
                        unicodeUpload
                );

        Candidate unicodeCandidate =
                candidate(
                        unicodeUpload,
                        "Rəşad Əliyev",
                        7,
                        "Bakı, Azərbaycan",
                        JobType.REMOTE,
                        "rəşad-cv.pdf",
                        Set.of(
                                "UNICODE_TEST",
                                "Java",
                                "Ödəniş sistemləri"
                        )
                );

        candidateRepository.saveAndFlush(
                unicodeCandidate
        );

        MvcResult result =
                performXlsxExport(
                        get(
                                EXPORT_URL
                        )
                                .param(
                                        "skill",
                                        "UNICODE_TEST"
                                )
                );

        try (
                XSSFWorkbook workbook =
                        workbook(
                                result
                        )
        ) {

            XSSFSheet sheet =
                    workbook.getSheet(
                            "Candidates"
                    );

            assertThat(
                    sheet
            ).isNotNull();

            /*
             * Header + 1 candidate.
             */

            assertThat(
                    sheet.getPhysicalNumberOfRows()
            ).isEqualTo(
                    2
            );

            assertThat(
                    stringCell(
                            sheet,
                            1,
                            2
                    )
            ).isEqualTo(
                    "Rəşad Əliyev"
            );

            assertThat(
                    stringCell(
                            sheet,
                            1,
                            4
                    )
            ).isEqualTo(
                    "Bakı, Azərbaycan"
            );

            String skills =
                    stringCell(
                            sheet,
                            1,
                            6
                    );

            assertThat(
                    skills
            )
                    .contains(
                            "Java"
                    )
                    .contains(
                            "Ödəniş sistemləri"
                    )
                    .contains(
                            "UNICODE_TEST"
                    );

            assertThat(
                    stringCell(
                            sheet,
                            1,
                            7
                    )
            ).isEqualTo(
                    "rəşad-cv.pdf"
            );
        }
    }

    /*
     * ============================================================
     * TEST 4
     *
     * FORMULA INJECTION SAFETY
     * ============================================================
     */

    @Test
    void shouldStoreFormulaLookingValueAsPlainString()
            throws Exception {

        CvUpload formulaUpload =
                new CvUpload(
                        "formula-candidates.zip"
                );

        formulaUpload.registerDiscoveredFiles(
                1
        );

        formulaUpload =
                cvUploadRepository.saveAndFlush(
                        formulaUpload
                );

        Candidate formulaCandidate =
                candidate(
                        formulaUpload,
                        "=2+2",
                        3,
                        "Baku",
                        JobType.REMOTE,
                        "formula.pdf",
                        Set.of(
                                "FORMULA_TEST"
                        )
                );

        candidateRepository.saveAndFlush(
                formulaCandidate
        );

        MvcResult result =
                performXlsxExport(
                        get(
                                EXPORT_URL
                        )
                                .param(
                                        "skill",
                                        "FORMULA_TEST"
                                )
                );

        try (
                XSSFWorkbook workbook =
                        workbook(
                                result
                        )
        ) {

            XSSFSheet sheet =
                    workbook.getSheet(
                            "Candidates"
                    );

            assertThat(
                    sheet
            ).isNotNull();

            Cell nameCell =
                    sheet
                            .getRow(
                                    1
                            )
                            .getCell(
                                    2
                            );

            assertThat(
                    nameCell
            ).isNotNull();

            assertThat(
                    nameCell.getCellType()
            ).isEqualTo(
                    CellType.STRING
            );

            assertThat(
                    nameCell.getStringCellValue()
            ).isEqualTo(
                    "=2+2"
            );

            assertThat(
                    nameCell.getCellType()
            ).isNotEqualTo(
                    CellType.FORMULA
            );
        }
    }

    /*
     * ============================================================
     * TEST 5
     *
     * EMPTY RESULT
     * ============================================================
     */

    @Test
    void shouldReturnHeaderOnlyWorkbookWhenNoCandidateMatches()
            throws Exception {

        MvcResult result =
                performXlsxExport(
                        get(
                                EXPORT_URL
                        )
                                .param(
                                        "skill",
                                        "Rust"
                                )
                );

        try (
                XSSFWorkbook workbook =
                        workbook(
                                result
                        )
        ) {

            XSSFSheet sheet =
                    workbook.getSheet(
                            "Candidates"
                    );

            assertThat(
                    sheet
            ).isNotNull();

            assertThat(
                    sheet.getPhysicalNumberOfRows()
            ).isEqualTo(
                    1
            );

            assertHeader(
                    sheet.getRow(
                            0
                    )
            );
        }
    }

    /*
     * ============================================================
     * TEST 6
     *
     * INVALID REQUEST MUST FAIL BEFORE STREAMING
     * ============================================================
     */

    @Test
    void shouldReturnJson400BeforeStreamingForInvalidSort()
            throws Exception {

        mockMvc.perform(
                        get(
                                EXPORT_URL
                        )
                                .param(
                                        "sortBy",
                                        "somethingDangerous"
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
                        header()
                                .string(
                                        HttpHeaders.CONTENT_TYPE,
                                        containsString(
                                                "application/json"
                                        )
                                )
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
                                "unsupported sort field"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.path"
                        ).value(
                                EXPORT_URL
                        )
                );
    }

    /*
     * ============================================================
     * TEST 7
     *
     * MULTI-BATCH EXPORT
     * +
     * N+1 GUARD
     * ============================================================
     */

    @Test
    void shouldExportMultipleBatchesWithoutNPlusOne()
            throws Exception {

        candidateRepository.deleteAll();

        cvUploadRepository.deleteAll();

        CvUpload bulkUpload =
                new CvUpload(
                        "bulk-candidates.zip"
                );

        bulkUpload.registerDiscoveredFiles(
                260
        );

        bulkUpload =
                cvUploadRepository.saveAndFlush(
                        bulkUpload
                );

        List<Candidate> candidates =
                new ArrayList<>(
                        260
                );

        for (
                int index = 0;
                index < 260;
                index++
        ) {

            candidates.add(
                    candidate(
                            bulkUpload,

                            String.format(
                                    "Bulk Candidate %03d",
                                    index
                            ),

                            index % 20,

                            "Baku",

                            JobType.REMOTE,

                            String.format(
                                    "bulk-%03d.pdf",
                                    index
                            ),

                            Set.of(
                                    "Java",
                                    "Bulk"
                            )
                    )
            );
        }

        candidateRepository.saveAllAndFlush(
                candidates
        );

        assertThat(
                candidateRepository.count()
        ).isEqualTo(
                260
        );

        /*
         * Dataset yaratma query-lərini
         * performance ölçümünə daxil etmirik.
         */

        Statistics statistics =
                statistics();

        statistics.clear();

        MvcResult result =
                performXlsxExport(
                        get(
                                EXPORT_URL
                        )
                                .param(
                                        "sortBy",
                                        "fullName"
                                )
                                .param(
                                        "direction",
                                        "asc"
                                )
                );

        try (
                XSSFWorkbook workbook =
                        workbook(
                                result
                        )
        ) {

            XSSFSheet sheet =
                    workbook.getSheet(
                            "Candidates"
                    );

            assertThat(
                    sheet
            ).isNotNull();

            /*
             * 1 header
             * +
             * 260 candidates
             */

            assertThat(
                    sheet.getPhysicalNumberOfRows()
            ).isEqualTo(
                    261
            );

            assertThat(
                    stringCell(
                            sheet,
                            1,
                            2
                    )
            ).isEqualTo(
                    "Bulk Candidate 000"
            );

            assertThat(
                    stringCell(
                            sheet,
                            260,
                            2
                    )
            ).isEqualTo(
                    "Bulk Candidate 259"
            );
        }

        /*
         * Expected architecture:
         *
         * batch 1:
         * page + hydration
         *
         * batch 2:
         * page + hydration
         *
         * Implementation variance:
         *
         * 4..8
         */

        long preparedStatements =
                statistics
                        .getPrepareStatementCount();

        assertThat(
                preparedStatements
        ).isBetween(
                4L,
                8L
        );
    }

    /*
     * ============================================================
     * ASYNC XLSX EXPORT HELPER
     * ============================================================
     *
     * Bütün normal XLSX export request-ləri
     * RECRUITER authority ilə göndərilir.
     */

    private MvcResult performXlsxExport(
            MockHttpServletRequestBuilder requestBuilder
    ) throws Exception {

        MvcResult initialResult =
                mockMvc.perform(
                                requestBuilder
                                        .with(
                                                SecurityTestUsers.recruiter()
                                        )
                        )
                        .andExpect(
                                request()
                                        .asyncStarted()
                        )
                        .andReturn();

        return mockMvc.perform(
                        asyncDispatch(
                                initialResult
                        )
                )
                .andExpect(
                        status()
                                .isOk()
                )
                .andReturn();
    }

    /*
     * ============================================================
     * RESPONSE BYTES -> XSSFWorkbook
     * ============================================================
     */

    private XSSFWorkbook workbook(
            MvcResult result
    ) throws IOException {

        byte[] bytes =
                result
                        .getResponse()
                        .getContentAsByteArray();

        /*
         * XLSX əslində ZIP container-dir.
         *
         * ZIP magic:
         *
         * 50 4B
         *
         * ASCII:
         *
         * PK
         */

        assertThat(
                bytes.length
        ).isGreaterThan(
                4
        );

        assertThat(
                bytes[0]
        ).isEqualTo(
                (byte) 0x50
        );

        assertThat(
                bytes[1]
        ).isEqualTo(
                (byte) 0x4B
        );

        return new XSSFWorkbook(
                new ByteArrayInputStream(
                        bytes
                )
        );
    }

    /*
     * ============================================================
     * HEADER VALIDATION
     * ============================================================
     */

    private void assertHeader(
            Row row
    ) {

        assertThat(
                row
        ).isNotNull();

        assertThat(
                row.getCell(
                                0
                        )
                        .getStringCellValue()
        ).isEqualTo(
                "candidateId"
        );

        assertThat(
                row.getCell(
                                1
                        )
                        .getStringCellValue()
        ).isEqualTo(
                "uploadId"
        );

        assertThat(
                row.getCell(
                                2
                        )
                        .getStringCellValue()
        ).isEqualTo(
                "fullName"
        );

        assertThat(
                row.getCell(
                                3
                        )
                        .getStringCellValue()
        ).isEqualTo(
                "yearsOfExperience"
        );

        assertThat(
                row.getCell(
                                4
                        )
                        .getStringCellValue()
        ).isEqualTo(
                "preferredLocation"
        );

        assertThat(
                row.getCell(
                                5
                        )
                        .getStringCellValue()
        ).isEqualTo(
                "preferredJobType"
        );

        assertThat(
                row.getCell(
                                6
                        )
                        .getStringCellValue()
        ).isEqualTo(
                "skills"
        );

        assertThat(
                row.getCell(
                                7
                        )
                        .getStringCellValue()
        ).isEqualTo(
                "sourceFilename"
        );
    }

    /*
     * ============================================================
     * STRING CELL HELPER
     * ============================================================
     */

    private String stringCell(
            XSSFSheet sheet,
            int rowIndex,
            int columnIndex
    ) {

        Row row =
                sheet.getRow(
                        rowIndex
                );

        assertThat(
                row
        ).isNotNull();

        Cell cell =
                row.getCell(
                        columnIndex
                );

        assertThat(
                cell
        ).isNotNull();

        assertThat(
                cell.getCellType()
        ).isEqualTo(
                CellType.STRING
        );

        return cell.getStringCellValue();
    }

    /*
     * ============================================================
     * CANDIDATE FACTORY
     * ============================================================
     */

    private Candidate candidate(
            CvUpload upload,
            String fullName,
            Integer yearsOfExperience,
            String location,
            JobType jobType,
            String sourceFilename,
            Set<String> skills
    ) {

        return new Candidate(
                upload,
                fullName,
                yearsOfExperience,
                location,
                jobType,
                sourceFilename,
                skills
        );
    }

    /*
     * ============================================================
     * HIBERNATE STATISTICS
     * ============================================================
     */

    private Statistics statistics() {

        SessionFactory sessionFactory =
                entityManagerFactory.unwrap(
                        SessionFactory.class
                );

        return sessionFactory
                .getStatistics();
    }

    /*
     * ============================================================
     * TEMP STORAGE ROOT
     * ============================================================
     */

    private static Path createStorageRoot() {

        try {

            return Files.createTempDirectory(
                    "cvscanner-xlsx-export-it-"
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