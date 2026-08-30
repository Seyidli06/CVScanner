package com.adil.cvscanner.candidate.api;

import com.adil.cvscanner.candidate.domain.Candidate;
import com.adil.cvscanner.candidate.domain.JobType;
import com.adil.cvscanner.candidate.infrastructure.CandidateRepository;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CandidateControllerIT {

    private static final Path STORAGE_ROOT =
            createStorageRoot();

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    "postgres:16-alpine"
            )
                    .withDatabaseName(
                            "cvscanner_candidate_api_test"
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

    private CvUpload backendUpload;

    private CvUpload secondUpload;

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

    @BeforeEach
    void setUp() {

        candidateRepository.deleteAll();

        cvUploadRepository.deleteAll();

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
                java.util.List.of(
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

    @Test
    void shouldReturnCandidatesWithDefaultPagination()
            throws Exception {

        mockMvc.perform(
                        recruiterGet(
                                "/api/v1/candidates"
                        )
                )
                .andExpect(
                        status().isOk()
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
                                6
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
                                "$.content[0].fullName"
                        ).value(
                                "Alice Johnson"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.content[5].fullName"
                        ).value(
                                "Farid Mammadov"
                        )
                );
    }

    @Test
    void shouldPaginateCandidates()
            throws Exception {

        mockMvc.perform(
                        recruiterGet(
                                "/api/v1/candidates"
                        )
                                .param(
                                        "page",
                                        "1"
                                )
                                .param(
                                        "size",
                                        "2"
                                )
                                .param(
                                        "sortBy",
                                        "fullName"
                                )
                                .param(
                                        "direction",
                                        "asc"
                                )
                )
                .andExpect(
                        status().isOk()
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
                                6
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
                                "$.content[0].fullName"
                        ).value(
                                "Charlie Brown"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.content[1].fullName"
                        ).value(
                                "David Wilson"
                        )
                );
    }

    @Test
    void shouldFilterByExactSkill()
            throws Exception {

        mockMvc.perform(
                        recruiterGet(
                                "/api/v1/candidates"
                        )
                                .param(
                                        "skill",
                                        "Java"
                                )
                                .param(
                                        "size",
                                        "20"
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.totalElements"
                        ).value(
                                4
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.content[*].fullName",
                                containsInAnyOrder(
                                        "Alice Johnson",
                                        "Bob Smith",
                                        "Elvin Aliyev",
                                        "Farid Mammadov"
                                )
                        )
                );
    }

    @Test
    void shouldFilterSkillCaseInsensitively()
            throws Exception {

        mockMvc.perform(
                        recruiterGet(
                                "/api/v1/candidates"
                        )
                                .param(
                                        "skill",
                                        "jAvA"
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.totalElements"
                        ).value(
                                4
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.content[*].fullName",
                                containsInAnyOrder(
                                        "Alice Johnson",
                                        "Bob Smith",
                                        "Elvin Aliyev",
                                        "Farid Mammadov"
                                )
                        )
                );
    }

    @Test
    void shouldFilterLocationCaseInsensitively()
            throws Exception {

        mockMvc.perform(
                        recruiterGet(
                                "/api/v1/candidates"
                        )
                                .param(
                                        "location",
                                        "bAkU"
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.totalElements"
                        ).value(
                                4
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.content[*].fullName",
                                containsInAnyOrder(
                                        "Alice Johnson",
                                        "Bob Smith",
                                        "David Wilson",
                                        "Elvin Aliyev"
                                )
                        )
                );
    }

    @Test
    void shouldFilterByJobType()
            throws Exception {

        mockMvc.perform(
                        recruiterGet(
                                "/api/v1/candidates"
                        )
                                .param(
                                        "jobType",
                                        "REMOTE"
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.totalElements"
                        ).value(
                                4
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.content[*].fullName",
                                containsInAnyOrder(
                                        "Alice Johnson",
                                        "Charlie Brown",
                                        "Elvin Aliyev",
                                        "Farid Mammadov"
                                )
                        )
                );
    }

    @Test
    void shouldFilterByExperienceRange()
            throws Exception {

        mockMvc.perform(
                        recruiterGet(
                                "/api/v1/candidates"
                        )
                                .param(
                                        "minExperience",
                                        "4"
                                )
                                .param(
                                        "maxExperience",
                                        "6"
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.totalElements"
                        ).value(
                                3
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.content[*].fullName",
                                containsInAnyOrder(
                                        "Alice Johnson",
                                        "David Wilson",
                                        "Elvin Aliyev"
                                )
                        )
                );
    }

    @Test
    void shouldFilterByUploadId()
            throws Exception {

        mockMvc.perform(
                        recruiterGet(
                                "/api/v1/candidates"
                        )
                                .param(
                                        "uploadId",
                                        backendUpload
                                                .getId()
                                                .toString()
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.totalElements"
                        ).value(
                                4
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.content[*].uploadId",
                                containsInAnyOrder(
                                        backendUpload
                                                .getId()
                                                .toString(),
                                        backendUpload
                                                .getId()
                                                .toString(),
                                        backendUpload
                                                .getId()
                                                .toString(),
                                        backendUpload
                                                .getId()
                                                .toString()
                                )
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.content[*].fullName",
                                containsInAnyOrder(
                                        "Alice Johnson",
                                        "Bob Smith",
                                        "Charlie Brown",
                                        "David Wilson"
                                )
                        )
                );
    }

    @Test
    void shouldCombineAllFiltersWithAndSemantics()
            throws Exception {

        mockMvc.perform(
                        recruiterGet(
                                "/api/v1/candidates"
                        )
                                .param(
                                        "uploadId",
                                        backendUpload
                                                .getId()
                                                .toString()
                                )
                                .param(
                                        "skill",
                                        "Java"
                                )
                                .param(
                                        "location",
                                        "Baku"
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
                                        "maxExperience",
                                        "6"
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.totalElements"
                        ).value(
                                1
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.content[0].fullName"
                        ).value(
                                "Alice Johnson"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.content[0].yearsOfExperience"
                        ).value(
                                5
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.content[0].preferredLocation"
                        ).value(
                                "Baku"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.content[0].preferredJobType"
                        ).value(
                                "REMOTE"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.content[0].skills",
                                hasItems(
                                        "Java",
                                        "Spring Boot",
                                        "PostgreSQL"
                                )
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.content[0].sourceFilename"
                        ).value(
                                "alice.pdf"
                        )
                );
    }

    @Test
    void shouldSortByExperienceDescending()
            throws Exception {

        mockMvc.perform(
                        recruiterGet(
                                "/api/v1/candidates"
                        )
                                .param(
                                        "sortBy",
                                        "yearsOfExperience"
                                )
                                .param(
                                        "direction",
                                        "desc"
                                )
                                .param(
                                        "size",
                                        "20"
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.content[0].fullName"
                        ).value(
                                "Farid Mammadov"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.content[0].yearsOfExperience"
                        ).value(
                                10
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.content[1].fullName"
                        ).value(
                                "Charlie Brown"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.content[2].fullName"
                        ).value(
                                "Elvin Aliyev"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.content[5].fullName"
                        ).value(
                                "Bob Smith"
                        )
                );
    }

    @Test
    void shouldReturn400WhenPageSizeExceedsLimit()
            throws Exception {

        mockMvc.perform(
                        recruiterGet(
                                "/api/v1/candidates"
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

    @Test
    void shouldReturn400WhenMinExperienceExceedsMaxExperience()
            throws Exception {

        mockMvc.perform(
                        recruiterGet(
                                "/api/v1/candidates"
                        )
                                .param(
                                        "minExperience",
                                        "10"
                                )
                                .param(
                                        "maxExperience",
                                        "3"
                                )
                )
                .andExpect(
                        status()
                                .isBadRequest()
                );
    }

    @Test
    void shouldReturn400ForUnsupportedSortField()
            throws Exception {

        mockMvc.perform(
                        recruiterGet(
                                "/api/v1/candidates"
                        )
                                .param(
                                        "sortBy",
                                        "somethingDangerous"
                                )
                )
                .andExpect(
                        status()
                                .isBadRequest()
                );
    }

    @Test
    void shouldReturn400ForUnsupportedSortDirection()
            throws Exception {

        mockMvc.perform(
                        recruiterGet(
                                "/api/v1/candidates"
                        )
                                .param(
                                        "direction",
                                        "sideways"
                                )
                )
                .andExpect(
                        status()
                                .isBadRequest()
                );
    }

    @Test
    void shouldReturnEmptyPageWhenNoCandidateMatches()
            throws Exception {

        mockMvc.perform(
                        recruiterGet(
                                "/api/v1/candidates"
                        )
                                .param(
                                        "skill",
                                        "Rust"
                                )
                )
                .andExpect(
                        status().isOk()
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

    




    private MockHttpServletRequestBuilder recruiterGet(
            String url
    ) {

        return get(
                url
        )
                .with(
                        SecurityTestUsers.recruiter()
                );
    }

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

    private static Path createStorageRoot() {

        try {

            return Files.createTempDirectory(
                    "cvscanner-candidate-controller-it-"
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