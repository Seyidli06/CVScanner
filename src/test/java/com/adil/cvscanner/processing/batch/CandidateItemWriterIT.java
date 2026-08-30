package com.adil.cvscanner.processing.batch;

import com.adil.cvscanner.candidate.application.CandidateDraft;
import com.adil.cvscanner.candidate.domain.Candidate;
import com.adil.cvscanner.candidate.domain.JobType;
import com.adil.cvscanner.candidate.infrastructure.CandidateRepository;
import com.adil.cvscanner.upload.domain.CvUpload;
import com.adil.cvscanner.upload.infrastructure.CvUploadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class CandidateItemWriterIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    "postgres:16-alpine"
            )
                    .withDatabaseName(
                            "cvscanner_test"
                    )
                    .withUsername(
                            "test"
                    )
                    .withPassword(
                            "test"
                    );

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired
    private CvUploadRepository cvUploadRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanDatabase() {

        





        candidateRepository.deleteAll();

        cvUploadRepository.deleteAll();
    }

    @Test
    void shouldSkipCandidateThatAlreadyExistsForSameUpload()
            throws Exception {

        





        CvUpload upload =
                createUpload(
                        "candidates.zip",
                        2
                );

        UUID uploadId =
                upload.getId();

        















        Candidate existingJohn =
                new Candidate(
                        upload,
                        "John Smith",
                        5,
                        "Baku",
                        JobType.REMOTE,
                        "john.pdf",
                        Set.of(
                                "Java",
                                "Spring Boot"
                        )
                );

        candidateRepository.saveAndFlush(
                existingJohn
        );

        assertThat(
                candidateRepository.count()
        ).isEqualTo(1);

        





        CandidateItemWriter writer =
                new CandidateItemWriter(
                        uploadId,
                        cvUploadRepository,
                        candidateRepository
                );

        








        CandidateDraft duplicateJohn =
                new CandidateDraft(
                        "John Changed",
                        10,
                        "London",
                        JobType.HYBRID,
                        Set.of(
                                "Java",
                                "Kafka"
                        ),
                        "john.pdf"
                );

        CandidateDraft newJane =
                new CandidateDraft(
                        "Jane Doe",
                        7,
                        "Baku",
                        JobType.HYBRID,
                        Set.of(
                                "Java",
                                "Spring Boot",
                                "Redis"
                        ),
                        "jane.docx"
                );

        Chunk<CandidateDraft> chunk =
                Chunk.of(
                        duplicateJohn,
                        newJane
                );

        







        TransactionTemplate transactionTemplate =
                new TransactionTemplate(
                        transactionManager
                );

        transactionTemplate.executeWithoutResult(
                status -> {

                    try {

                        writer.write(
                                chunk
                        );

                    } catch (Exception exception) {

                        throw new RuntimeException(
                                exception
                        );
                    }
                }
        );

        












        List<Candidate> candidates =
                candidateRepository
                        .findAllByUpload_Id(
                                uploadId
                        );

        assertThat(
                candidates
        ).hasSize(2);

        assertThat(
                candidates
        )
                .extracting(
                        Candidate::getSourceFilename
                )
                .containsExactlyInAnyOrder(
                        "john.pdf",
                        "jane.docx"
                );

        


        Candidate john =
                candidates
                        .stream()
                        .filter(
                                candidate ->
                                        "john.pdf".equals(
                                                candidate.getSourceFilename()
                                        )
                        )
                        .findFirst()
                        .orElseThrow();

        assertThat(
                john.getFullName()
        ).isEqualTo(
                "John Smith"
        );

        assertThat(
                john.getYearsOfExperience()
        ).isEqualTo(
                5
        );

        







        assertThat(
                candidates
        )
                .extracting(
                        Candidate::getFullName
                )
                .doesNotContain(
                        "John Changed"
                );

        assertThat(
                candidates
        )
                .extracting(
                        Candidate::getFullName
                )
                .contains(
                        "Jane Doe"
                );
    }

    @Test
    void shouldIgnoreDuplicateSourceFilenameInsideSameChunk()
            throws Exception {

        CvUpload upload =
                createUpload(
                        "same-chunk.zip",
                        1
                );

        CandidateItemWriter writer =
                new CandidateItemWriter(
                        upload.getId(),
                        cvUploadRepository,
                        candidateRepository
                );

        






        CandidateDraft first =
                new CandidateDraft(
                        "John Smith",
                        5,
                        "Baku",
                        JobType.REMOTE,
                        Set.of(
                                "Java"
                        ),
                        "john.pdf"
                );

        CandidateDraft second =
                new CandidateDraft(
                        "John Duplicate",
                        8,
                        "Berlin",
                        JobType.HYBRID,
                        Set.of(
                                "Kafka"
                        ),
                        "john.pdf"
                );

        Chunk<CandidateDraft> chunk =
                Chunk.of(
                        first,
                        second
                );

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(
                        transactionManager
                );

        transactionTemplate.executeWithoutResult(
                status -> {

                    try {

                        writer.write(
                                chunk
                        );

                    } catch (Exception exception) {

                        throw new RuntimeException(
                                exception
                        );
                    }
                }
        );

        List<Candidate> candidates =
                candidateRepository
                        .findAllByUpload_Id(
                                upload.getId()
                        );

        



        assertThat(
                candidates
        ).hasSize(1);

        assertThat(
                candidates.getFirst()
                        .getSourceFilename()
        ).isEqualTo(
                "john.pdf"
        );

        


        assertThat(
                candidates.getFirst()
                        .getFullName()
        ).isEqualTo(
                "John Smith"
        );
    }

    @Test
    void shouldAllowSameSourceFilenameForDifferentUploads() {

        








        CvUpload firstUpload =
                createUpload(
                        "first.zip",
                        1
                );

        CvUpload secondUpload =
                createUpload(
                        "second.zip",
                        1
                );

        Candidate firstJohn =
                new Candidate(
                        firstUpload,
                        "John Smith",
                        5,
                        "Baku",
                        JobType.REMOTE,
                        "john.pdf",
                        Set.of(
                                "Java"
                        )
                );

        Candidate secondJohn =
                new Candidate(
                        secondUpload,
                        "John Smith",
                        5,
                        "Baku",
                        JobType.REMOTE,
                        "john.pdf",
                        Set.of(
                                "Java"
                        )
                );

        candidateRepository.saveAndFlush(
                firstJohn
        );

        candidateRepository.saveAndFlush(
                secondJohn
        );

        assertThat(
                candidateRepository
                        .findAllByUpload_Id(
                                firstUpload.getId()
                        )
        ).hasSize(1);

        assertThat(
                candidateRepository
                        .findAllByUpload_Id(
                                secondUpload.getId()
                        )
        ).hasSize(1);

        assertThat(
                candidateRepository.count()
        ).isEqualTo(2);
    }

    @Test
    void shouldRejectDuplicateAtDatabaseLevel() {

        










        CvUpload upload =
                createUpload(
                        "db-constraint.zip",
                        1
                );

        Candidate first =
                new Candidate(
                        upload,
                        "John Smith",
                        5,
                        "Baku",
                        JobType.REMOTE,
                        "john.pdf",
                        Set.of(
                                "Java"
                        )
                );

        candidateRepository.saveAndFlush(
                first
        );

        Candidate duplicate =
                new Candidate(
                        upload,
                        "Another John",
                        10,
                        "London",
                        JobType.HYBRID,
                        "john.pdf",
                        Set.of(
                                "Kafka"
                        )
                );

        







        assertThatThrownBy(
                () ->
                        candidateRepository
                                .saveAndFlush(
                                        duplicate
                                )
        )
                .isInstanceOf(
                        DataIntegrityViolationException.class
                );
    }

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

        return cvUploadRepository
                .saveAndFlush(
                        upload
                );
    }
}