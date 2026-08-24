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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(
        properties = {
                "spring.jpa.properties[hibernate.generate_statistics]=true"
        }
)
class CandidateQueryPerformanceIT {

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
                            "cvscanner_candidate_performance_test"
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

    private Statistics statistics;

    /*
     * ============================================================
     * DATASET
     * ============================================================
     *
     * 30 candidate yaradırıq.
     *
     * Request:
     *
     * page=0
     * size=20
     *
     * Beləliklə:
     *
     * - page full olacaq
     * - database-də əlavə 10 nəticə qalacaq
     *
     * və Spring Data totalElements-i tapmaq
     * üçün count query işlətməli olacaq.
     */

    @BeforeEach
    void setUp() {

        candidateRepository.deleteAll();

        cvUploadRepository.deleteAll();

        /*
         * Hibernate native SessionFactory.
         */

        SessionFactory sessionFactory =
                entityManagerFactory.unwrap(
                        SessionFactory.class
                );

        statistics =
                sessionFactory.getStatistics();

        /*
         * Statistics property həqiqətən aktivdir.
         */

        assertThat(
                statistics.isStatisticsEnabled()
        ).isTrue();

        /*
         * =====================================================
         * UPLOAD
         * =====================================================
         */

        CvUpload upload =
                new CvUpload(
                        "performance-test.zip"
                );

        upload.registerDiscoveredFiles(
                30
        );

        upload =
                cvUploadRepository.saveAndFlush(
                        upload
                );

        /*
         * =====================================================
         * 30 CANDIDATES
         * =====================================================
         */

        List<Candidate> candidates =
                new ArrayList<>();

        for (
                int index = 1;
                index <= 30;
                index++
        ) {

            Candidate candidate =
                    new Candidate(
                            upload,

                            /*
                             * Zero padding sort-u
                             * deterministic edir:
                             *
                             * Candidate 01
                             * Candidate 02
                             * ...
                             * Candidate 30
                             */
                            "Candidate %02d".formatted(
                                    index
                            ),

                            index % 11,

                            "Baku",

                            JobType.REMOTE,

                            "candidate-%02d.pdf".formatted(
                                    index
                            ),

                            /*
                             * Hər Candidate bir neçə skill daşıyır.
                             *
                             * N+1 varsa bu dataset
                             * onu çox aydın göstərəcək.
                             */
                            Set.of(
                                    "Java",
                                    "Spring Boot",
                                    "PostgreSQL",
                                    "Docker",
                                    "Redis"
                            )
                    );

            candidates.add(
                    candidate
            );
        }

        candidateRepository.saveAllAndFlush(
                candidates
        );

        assertThat(
                candidateRepository.count()
        ).isEqualTo(
                30
        );

        /*
         * =====================================================
         * CRITICAL
         * =====================================================
         *
         * Setup zamanı çoxlu INSERT/SELECT
         * statement işlətdik.
         *
         * Performance measurement yalnız
         * HTTP request-i ölçməlidir.
         *
         * Ona görə bütün əvvəlki statistics-i
         * burada sıfırlayırıq.
         */

        statistics.clear();

        assertThat(
                statistics.getPrepareStatementCount()
        ).isZero();
    }

    /*
     * ============================================================
     * MAIN PERFORMANCE TEST
     * ============================================================
     */

    @Test
    void shouldLoadCandidatePageWithoutNPlusOneQueries()
            throws Exception {

        /*
         * =====================================================
         * REQUEST
         * =====================================================
         *
         * 30 candidate var.
         *
         * İlk 20-ni istəyirik.
         */

        mockMvc.perform(
                        get(
                                "/api/v1/candidates"
                        )
                                .param(
                                        "page",
                                        "0"
                                )
                                .param(
                                        "size",
                                        "20"
                                )
                                .param(
                                        "sortBy",
                                        "fullName"
                                )
                                .param(
                                        "direction",
                                        "asc"
                                )
                                .with(
                                        SecurityTestUsers.recruiter()
                                )
                )
                .andExpect(
                        status()
                                .isOk()
                )

                /*
                 * API business result də
                 * düzgün qalmalıdır.
                 */
                .andExpect(
                        jsonPath(
                                "$.content.length()"
                        ).value(
                                20
                        )
                )

                .andExpect(
                        jsonPath(
                                "$.totalElements"
                        ).value(
                                30
                        )
                )

                .andExpect(
                        jsonPath(
                                "$.totalPages"
                        ).value(
                                2
                        )
                )

                .andExpect(
                        jsonPath(
                                "$.content[0].fullName"
                        ).value(
                                "Candidate 01"
                        )
                )

                .andExpect(
                        jsonPath(
                                "$.content[19].fullName"
                        ).value(
                                "Candidate 20"
                        )
                )

                .andExpect(
                        jsonPath(
                                "$.content[0].skills.length()"
                        ).value(
                                5
                        )
                );

        /*
         * =====================================================
         * SQL QUERY COUNT
         * =====================================================
         */

        long preparedStatements =
                statistics
                        .getPrepareStatementCount();

        /*
         * Candidate page çoxlu SQL query
         * yaratmamalıdır.
         *
         * N+1 regression olsa bu say
         * candidate sayı ilə birlikdə artacaq.
         */

        assertThat(
                preparedStatements
        )
                .as(
                        "Candidate page generated too many SQL statements; possible N+1 regression"
                )
                .isLessThanOrEqualTo(
                        4
                );

        /*
         * Ən az:
         *
         * page/select query
         * +
         * count və ya əlaqəli query
         *
         * gözləyirik.
         */

        assertThat(
                preparedStatements
        ).isGreaterThanOrEqualTo(
                2
        );
    }
}