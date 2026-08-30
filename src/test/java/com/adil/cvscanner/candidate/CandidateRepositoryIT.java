package com.adil.cvscanner.candidate;

import com.adil.cvscanner.candidate.domain.Candidate;
import com.adil.cvscanner.candidate.domain.JobType;
import com.adil.cvscanner.candidate.infrastructure.CandidateRepository;
import com.adil.cvscanner.upload.domain.CvUpload;
import com.adil.cvscanner.upload.infrastructure.CvUploadRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Transactional
class CandidateRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16-alpine")
                    .withDatabaseName("cvscanner_test")
                    .withUsername("test")
                    .withPassword("test");

    @Autowired
    private CvUploadRepository cvUploadRepository;

    @Autowired
    private CandidateRepository candidateRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void shouldPersistCandidateWithSkills() {

        CvUpload upload =
                cvUploadRepository.saveAndFlush(
                        new CvUpload(
                                "backend-candidates.zip"
                        )
                );

        Candidate candidate =
                new Candidate(
                        upload,
                        "John Smith",
                        5,
                        "Baku",
                        JobType.REMOTE,
                        "john-smith.pdf",
                        Set.of(
                                "Java",
                                "Spring Boot",
                                "PostgreSQL"
                        )
                );

        Candidate saved =
                candidateRepository.saveAndFlush(
                        candidate
                );

        UUID candidateId =
                saved.getId();

        assertThat(candidateId)
                .isNotNull();

        






        entityManager.clear();

        Candidate found =
                candidateRepository
                        .findById(candidateId)
                        .orElseThrow();

        assertThat(found.getFullName())
                .isEqualTo("John Smith");

        assertThat(
                found.getYearsOfExperience()
        ).isEqualTo(5);

        assertThat(
                found.getPreferredLocation()
        ).isEqualTo("Baku");

        assertThat(
                found.getPreferredJobType()
        ).isEqualTo(JobType.REMOTE);

        assertThat(
                found.getSourceFilename()
        ).isEqualTo("john-smith.pdf");

        assertThat(
                found.getSkills()
        ).containsExactlyInAnyOrder(
                "Java",
                "Spring Boot",
                "PostgreSQL"
        );
    }
}