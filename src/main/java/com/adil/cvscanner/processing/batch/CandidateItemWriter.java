package com.adil.cvscanner.processing.batch;

import com.adil.cvscanner.candidate.application.CandidateDraft;
import com.adil.cvscanner.candidate.domain.Candidate;
import com.adil.cvscanner.candidate.infrastructure.CandidateRepository;
import com.adil.cvscanner.upload.domain.CvUpload;
import com.adil.cvscanner.upload.infrastructure.CvUploadRepository;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class CandidateItemWriter
        implements ItemWriter<CandidateDraft> {

    private final UUID uploadId;

    private final CvUploadRepository cvUploadRepository;

    private final CandidateRepository candidateRepository;

    public CandidateItemWriter(
            UUID uploadId,
            CvUploadRepository cvUploadRepository,
            CandidateRepository candidateRepository
    ) {
        this.uploadId =
                uploadId;

        this.cvUploadRepository =
                cvUploadRepository;

        this.candidateRepository =
                candidateRepository;
    }

    @Override
    public void write(
            Chunk<? extends CandidateDraft> chunk
    ) {

        if (chunk.isEmpty()) {
            return;
        }

        





        CvUpload upload =
                cvUploadRepository
                        .findById(uploadId)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "CvUpload not found for candidate persistence: "
                                                        + uploadId
                                        )
                        );

        










        Set<String> sourceFilenames =
                new LinkedHashSet<>();

        for (
                CandidateDraft draft : chunk
        ) {

            validateDraft(
                    draft
            );

            sourceFilenames.add(
                    draft.sourceFilename()
            );
        }

        



















        Set<String> existingSourceFilenames =
                new HashSet<>(
                        candidateRepository
                                .findExistingSourceFilenames(
                                        uploadId,
                                        sourceFilenames
                                )
                );

        










        Set<String> seenInCurrentChunk =
                new HashSet<>();

        List<Candidate> candidatesToInsert =
                new ArrayList<>();

        for (
                CandidateDraft draft : chunk
        ) {

            String sourceFilename =
                    draft.sourceFilename();

            






            if (
                    existingSourceFilenames.contains(
                            sourceFilename
                    )
            ) {
                continue;
            }

            


            if (
                    !seenInCurrentChunk.add(
                            sourceFilename
                    )
            ) {
                continue;
            }

            Candidate candidate =
                    new Candidate(
                            upload,
                            draft.fullName(),
                            draft.yearsOfExperience(),
                            draft.preferredLocation(),
                            draft.preferredJobType(),
                            sourceFilename,
                            draft.skills()
                    );

            candidatesToInsert.add(
                    candidate
            );
        }

        



        if (
                candidatesToInsert.isEmpty()
        ) {
            return;
        }

        





        candidateRepository.saveAll(
                candidatesToInsert
        );

        





        candidateRepository.flush();
    }

    private void validateDraft(
            CandidateDraft draft
    ) {

        if (
                draft == null
        ) {

            throw new IllegalArgumentException(
                    "CandidateDraft must not be null"
            );
        }

        if (
                draft.sourceFilename() == null
                        || draft.sourceFilename()
                        .isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Candidate sourceFilename must not be blank"
            );
        }
    }
}