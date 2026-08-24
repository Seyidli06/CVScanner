package com.adil.cvscanner.candidate.api;

import com.adil.cvscanner.candidate.domain.Candidate;
import com.adil.cvscanner.candidate.domain.JobType;
import com.adil.cvscanner.candidate.infrastructure.CandidateRepository;
import com.adil.cvscanner.security.SecurityTestUsers;
import com.adil.cvscanner.upload.domain.CvUpload;
import com.adil.cvscanner.upload.infrastructure.CvUploadRepository;
import jakarta.persistence.EntityManagerFactory;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
class CandidateCsvExportControllerIT {

    /*
     * ============================================================
     * CONSTANTS
     * ============================================================
     */

    private static final String EXPORT_URL =
            "/api/v1/candidates/export.csv";

    private static final String EXPECTED_HEADER =
            "\"candidateId\","
                    + "\"uploadId\","
                    + "\"fullName\","
                    + "\"yearsOfExperience\","
                    + "\"preferredLocation\","
                    + "\"preferredJobType\","
                    + "\"skills\","
                    + "\"sourceFilename\"";

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
                            "cvscanner_csv_export_test"
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
    private CandidateRepository candidateRepository;

    @Autowired
    private CvUploadRepository cvUploadRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

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
     * Eyni CandidateControllerIT dataset-i:
     *
     * Alice    5   Baku      REMOTE
     * Bob      2   Baku      ONSITE
     * Charlie  8   Ganja     REMOTE
     * David    4   Baku      HYBRID
     * Elvin    6   Baku      REMOTE
     * Farid   10   Sumgait   REMOTE
     */

    @BeforeEach
    void setUp() {

        candidateRepository.deleteAll();

        cvUploadRepository.deleteAll();

        /*
         * =====================================================
         * UPLOAD #1
         * =====================================================
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
         * =====================================================
         * UPLOAD #2
         * =====================================================
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
         * =====================================================
         * CANDIDATES
         * =====================================================
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
     * DOWNLOAD CONTRACT + DEFAULT SORT
     * ============================================================
     */

    @Test
    void shouldDownloadCsvWithExpectedHeadersAndDefaultSort()
            throws Exception {

        MvcResult result =
                performCsvExport(
                        get(
                                EXPORT_URL
                        )
                );

        assertThat(
                result.getResponse().getStatus()
        ).isEqualTo(
                200
        );

        assertThat(
                result.getResponse()
                        .getHeader(
                                HttpHeaders.CONTENT_DISPOSITION
                        )
        ).isEqualTo(
                "attachment; filename=\"candidates.csv\""
        );

        assertThat(
                result.getResponse()
                        .getHeader(
                                HttpHeaders.CONTENT_TYPE
                        )
        ).contains(
                "text/csv"
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

        String csv =
                csvBody(
                        result
                );

        /*
         * UTF-8 BOM.
         */

        assertThat(
                csv
        ).startsWith(
                "\uFEFF"
        );

        assertThat(
                withoutBom(
                        csv
                )
        ).startsWith(
                EXPECTED_HEADER
        );

        /*
         * Header + 6 candidates.
         */

        assertThat(
                withoutBom(
                        csv
                ).lines().count()
        ).isEqualTo(
                7
        );

        /*
         * Default:
         *
         * fullName ASC
         */

        assertAppearsBefore(
                csv,
                "\"Alice Johnson\"",
                "\"Bob Smith\""
        );

        assertAppearsBefore(
                csv,
                "\"Bob Smith\"",
                "\"Charlie Brown\""
        );

        assertAppearsBefore(
                csv,
                "\"Charlie Brown\"",
                "\"David Wilson\""
        );

        assertAppearsBefore(
                csv,
                "\"David Wilson\"",
                "\"Elvin Aliyev\""
        );

        assertAppearsBefore(
                csv,
                "\"Elvin Aliyev\"",
                "\"Farid Mammadov\""
        );

        /*
         * Skills export-da deterministic
         * case-insensitive sort edilir.
         */

        assertThat(
                csv
        ).contains(
                "\"Java | PostgreSQL | Spring Boot\""
        );
    }

    /*
     * ============================================================
     * TEST 2
     * FILTERS + SORT
     * ============================================================
     *
     * skill = Java
     * jobType = REMOTE
     * minExperience = 3
     *
     * match:
     *
     * Farid 10
     * Elvin  6
     * Alice  5
     *
     * sort:
     *
     * experience DESC
     */

    @Test
    void shouldApplyFiltersAndSortExport()
            throws Exception {

        MvcResult result =
                performCsvExport(
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

        String csv =
                csvBody(
                        result
                );

        /*
         * Header + 3 matching candidates.
         */

        assertThat(
                withoutBom(
                        csv
                ).lines().count()
        ).isEqualTo(
                4
        );

        assertThat(
                csv
        )
                .contains(
                        "\"Farid Mammadov\""
                )
                .contains(
                        "\"Elvin Aliyev\""
                )
                .contains(
                        "\"Alice Johnson\""
                )
                .doesNotContain(
                        "\"Bob Smith\""
                )
                .doesNotContain(
                        "\"Charlie Brown\""
                )
                .doesNotContain(
                        "\"David Wilson\""
                );

        assertAppearsBefore(
                csv,
                "\"Farid Mammadov\"",
                "\"Elvin Aliyev\""
        );

        assertAppearsBefore(
                csv,
                "\"Elvin Aliyev\"",
                "\"Alice Johnson\""
        );
    }

    /*
     * ============================================================
     * TEST 3
     * UTF-8 + COMMA + QUOTE + NEWLINE ESCAPING
     * ============================================================
     */

    @Test
    void shouldEscapeCsvSpecialCharactersAndPreserveUtf8()
            throws Exception {

        CvUpload specialUpload =
                new CvUpload(
                        "special-candidates.zip"
                );

        specialUpload.registerDiscoveredFiles(
                1
        );

        specialUpload =
                cvUploadRepository.saveAndFlush(
                        specialUpload
                );

        Candidate specialCandidate =
                candidate(
                        specialUpload,

                        /*
                         * Contains:
                         *
                         * Azərbaycan characters
                         * quotes
                         * comma
                         * newline
                         */
                        "Rəşad \"QA\", Əliyev\nSenior",

                        7,

                        "Bakı, Azərbaycan",

                        JobType.REMOTE,

                        "special,\"resume\".pdf",

                        Set.of(
                                "CSV_TEST",
                                "Java"
                        )
                );

        candidateRepository.saveAndFlush(
                specialCandidate
        );

        MvcResult result =
                performCsvExport(
                        get(
                                EXPORT_URL
                        )
                                .param(
                                        "skill",
                                        "CSV_TEST"
                                )
                );

        String csv =
                csvBody(
                        result
                );

        /*
         * CSV quote escaping:
         *
         * "
         *
         * becomes:
         *
         * ""
         *
         * Embedded newline quoted cell daxilində
         * qalır.
         */

        assertThat(
                csv
        ).contains(
                "\"Rəşad \"\"QA\"\", Əliyev\nSenior\""
        );

        /*
         * Comma quoted cell daxilində təhlükəsizdir.
         */

        assertThat(
                csv
        ).contains(
                "\"Bakı, Azərbaycan\""
        );

        /*
         * Filename daxilində:
         *
         * comma + quotes
         */

        assertThat(
                csv
        ).contains(
                "\"special,\"\"resume\"\".pdf\""
        );

        /*
         * UTF-8 pozulmamalıdır.
         */

        assertThat(
                csv
        )
                .contains(
                        "Rəşad"
                )
                .contains(
                        "Əliyev"
                )
                .contains(
                        "Bakı"
                )
                .contains(
                        "Azərbaycan"
                );
    }

    /*
     * ============================================================
     * TEST 4
     * CSV FORMULA INJECTION
     * ============================================================
     *
     * Spreadsheet belə value-ni formula kimi
     * interpret edə bilər:
     *
     * =2+2
     *
     * Export:
     *
     * '=2+2
     *
     * etməlidir.
     */

    @Test
    void shouldProtectSpreadsheetFormulaInjection()
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
                performCsvExport(
                        get(
                                EXPORT_URL
                        )
                                .param(
                                        "skill",
                                        "FORMULA_TEST"
                                )
                );

        String csv =
                csvBody(
                        result
                );

        /*
         * Dangerous:
         *
         * "=2+2"
         *
         * olmamalıdır.
         */

        assertThat(
                csv
        ).doesNotContain(
                ",\"=2+2\","
        );

        /*
         * Safe:
         *
         * "'=2+2"
         */

        assertThat(
                csv
        ).contains(
                "\"'=2+2\""
        );
    }

    /*
     * ============================================================
     * TEST 5
     * EMPTY EXPORT
     * ============================================================
     *
     * Matching candidate yoxdursa:
     *
     * 200
     *
     * və yalnız CSV header.
     */

    @Test
    void shouldReturnHeaderOnlyWhenNoCandidateMatches()
            throws Exception {

        MvcResult result =
                performCsvExport(
                        get(
                                EXPORT_URL
                        )
                                .param(
                                        "skill",
                                        "Rust"
                                )
                );

        String csv =
                withoutBom(
                        csvBody(
                                result
                        )
                );

        assertThat(
                csv.strip()
        ).isEqualTo(
                EXPECTED_HEADER
        );
    }

    /*
     * ============================================================
     * TEST 6
     * INVALID REQUEST MUST FAIL BEFORE STREAMING
     * ============================================================
     *
     * Bu xüsusilə vacibdir.
     *
     * StreamingResponseBody başlayandan sonra
     * normal JSON error response qaytarmaq gec ola
     * bilər.
     *
     * Buna görə validation controller daxilində
     * pre-flight işləyir.
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
     * MULTI-BATCH EXPORT + N+1 GUARD
     * ============================================================
     *
     * Production export batch size:
     *
     * 250
     *
     * Burada 260 candidate yaradırıq.
     *
     * Deməli export ən az:
     *
     * batch #1 = 250
     * batch #2 = 10
     *
     * işlətməlidir.
     *
     *
     * Expected query architecture:
     *
     * batch 1:
     *   page query
     *   hydration query
     *
     * batch 2:
     *   page query
     *   hydration query
     *
     * Yəni yüzlərlə candidate olsa belə
     * N+1 olmamalıdır.
     */

    @Test
    void shouldExportMoreThanOneBatchWithoutNPlusOne()
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
         * Setup query-lərini performance ölçümünə
         * daxil etmirik.
         */

        Statistics statistics =
                statistics();

        statistics.clear();

        MvcResult result =
                performCsvExport(
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

        String csv =
                withoutBom(
                        csvBody(
                                result
                        )
                );

        /*
         * Header + 260 candidate.
         */

        assertThat(
                csv.lines().count()
        ).isEqualTo(
                261
        );

        assertThat(
                csv
        )
                .contains(
                        "\"Bulk Candidate 000\""
                )
                .contains(
                        "\"Bulk Candidate 259\""
                );

        assertAppearsBefore(
                csv,
                "\"Bulk Candidate 000\"",
                "\"Bulk Candidate 259\""
        );

        /*
         * 260 candidate üçün yüzlərlə SQL
         * görmək N+1 demək olardı.
         *
         * Hazırkı two-phase architecture-də
         * təxminən 4 statement gözləyirik:
         *
         * 2 batches x
         *   page + hydration.
         *
         * Bir qədər implementation variance
         * üçün 4..8 interval saxlayırıq.
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
     * ASYNC STREAMING HELPER
     * ============================================================
     *
     * StreamingResponseBody Spring MVC-də async
     * request kimi işləyir.
     *
     * Ona görə:
     *
     * 1. initial request
     * 2. asyncStarted
     * 3. asyncDispatch
     *
     * Final RBAC:
     *
     * bütün normal CSV export request-ləri
     * RECRUITER authority ilə gedir.
     */

    private MvcResult performCsvExport(
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
     * RESPONSE BODY
     * ============================================================
     */

    private String csvBody(
            MvcResult result
    ) {

        return new String(
                result
                        .getResponse()
                        .getContentAsByteArray(),
                StandardCharsets.UTF_8
        );
    }

    /*
     * ============================================================
     * BOM HELPER
     * ============================================================
     */

    private String withoutBom(
            String csv
    ) {

        if (
                csv != null
                        &&
                        !csv.isEmpty()
                        &&
                        csv.charAt(
                                0
                        ) == '\uFEFF'
        ) {

            return csv.substring(
                    1
            );
        }

        return csv;
    }

    /*
     * ============================================================
     * ORDER ASSERTION
     * ============================================================
     */

    private void assertAppearsBefore(
            String text,
            String first,
            String second
    ) {

        int firstIndex =
                text.indexOf(
                        first
                );

        int secondIndex =
                text.indexOf(
                        second
                );

        assertThat(
                firstIndex
        )
                .as(
                        "%s should exist",
                        first
                )
                .isGreaterThanOrEqualTo(
                        0
                );

        assertThat(
                secondIndex
        )
                .as(
                        "%s should exist",
                        second
                )
                .isGreaterThanOrEqualTo(
                        0
                );

        assertThat(
                firstIndex
        )
                .as(
                        "%s should appear before %s",
                        first,
                        second
                )
                .isLessThan(
                        secondIndex
                );
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
     * STORAGE ROOT
     * ============================================================
     */

    private static Path createStorageRoot() {

        try {

            return Files.createTempDirectory(
                    "cvscanner-csv-export-it-"
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