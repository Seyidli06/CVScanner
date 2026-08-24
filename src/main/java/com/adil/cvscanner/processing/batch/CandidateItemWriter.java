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

        /*
         * =====================================================
         * 1. CV UPLOAD
         * =====================================================
         */

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

        /*
         * =====================================================
         * 2. CHUNK-DƏKİ SOURCE FILENAMES
         * =====================================================
         *
         * LinkedHashSet:
         *
         * - duplicate-ləri silir
         * - deterministic order saxlayır
         */

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

        /*
         * =====================================================
         * 3. DB-DƏ ƏVVƏLDƏN OLANLARI TAP
         * =====================================================
         *
         * Bir query.
         *
         * Məsələn:
         *
         * chunk:
         * john.pdf
         * jane.docx
         *
         * DB:
         * john.pdf
         *
         * result:
         * john.pdf
         */

        Set<String> existingSourceFilenames =
                new HashSet<>(
                        candidateRepository
                                .findExistingSourceFilenames(
                                        uploadId,
                                        sourceFilenames
                                )
                );

        /*
         * =====================================================
         * 4. EYNİ CHUNK DAXİLİ DUPLICATE QORUMASI
         * =====================================================
         *
         * Normalda CvFileDiscoveryService eyni
         * Path-i iki dəfə verməməlidir.
         *
         * Amma writer özü də defensive olsun.
         */

        Set<String> seenInCurrentChunk =
                new HashSet<>();

        List<Candidate> candidatesToInsert =
                new ArrayList<>();

        for (
                CandidateDraft draft : chunk
        ) {

            String sourceFilename =
                    draft.sourceFilename();

            /*
             * DB-də artıq varsa:
             *
             * restart/retry zamanı duplicate-dir.
             *
             * Skip edirik.
             */
            if (
                    existingSourceFilenames.contains(
                            sourceFilename
                    )
            ) {
                continue;
            }

            /*
             * Eyni chunk daxilində duplicate.
             */
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

        /*
         * Hamısı artıq DB-dədirsə,
         * heç nə yazmağa ehtiyac yoxdur.
         */
        if (
                candidatesToInsert.isEmpty()
        ) {
            return;
        }

        /*
         * =====================================================
         * 5. PERSIST
         * =====================================================
         */

        candidateRepository.saveAll(
                candidatesToInsert
        );

        /*
         * UNIQUE constraint kimi DB xətalarının
         * chunk transaction bitəndən çox sonra
         * yox, elə writer daxilində üzə çıxmasını
         * istəyirik.
         */
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