package com.adil.cvscanner.candidate.api;

import com.adil.cvscanner.candidate.domain.Candidate;
import com.adil.cvscanner.candidate.domain.JobType;

import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public record CandidateResponse(

        UUID id,

        UUID uploadId,

        String fullName,

        Integer yearsOfExperience,

        String preferredLocation,

        JobType preferredJobType,

        Set<String> skills,

        String sourceFilename

) {

    public static CandidateResponse from(
            Candidate candidate
    ) {

        



        Set<String> sortedSkills =
                new TreeSet<>(
                        String.CASE_INSENSITIVE_ORDER
                );

        sortedSkills.addAll(
                candidate.getSkills()
        );

        return new CandidateResponse(
                candidate.getId(),
                candidate.getUpload()
                        .getId(),
                candidate.getFullName(),
                candidate.getYearsOfExperience(),
                candidate.getPreferredLocation(),
                candidate.getPreferredJobType(),
                Set.copyOf(
                        sortedSkills
                ),
                candidate.getSourceFilename()
        );
    }
}