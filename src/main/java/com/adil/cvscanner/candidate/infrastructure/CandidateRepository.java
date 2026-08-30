package com.adil.cvscanner.candidate.infrastructure;

import com.adil.cvscanner.candidate.domain.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CandidateRepository
        extends JpaRepository<Candidate, UUID>,
        JpaSpecificationExecutor<Candidate> {

    List<Candidate> findAllByUpload_Id(
            UUID uploadId
    );

    boolean existsByUpload_IdAndSourceFilename(
            UUID uploadId,
            String sourceFilename
    );

    @Query("""
            select c.sourceFilename
            from Candidate c
            where c.upload.id = :uploadId
              and c.sourceFilename in :sourceFilenames
            """)
    List<String> findExistingSourceFilenames(
            @Param("uploadId")
            UUID uploadId,

            @Param("sourceFilenames")
            Collection<String> sourceFilenames
    );

    @Query("""
            select distinct c
            from Candidate c
            join fetch c.upload
            left join fetch c.skills
            where c.id in :candidateIds
            """)
    List<Candidate> findAllWithResponseDetailsByIdIn(
            @Param("candidateIds")
            Collection<UUID> candidateIds
    );
}
