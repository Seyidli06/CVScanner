package com.adil.cvscanner.candidate.application;

import com.adil.cvscanner.candidate.domain.JobType;

import java.util.Set;

public record CandidateDraft(

        String fullName,

        Integer yearsOfExperience,

        String preferredLocation,

        JobType preferredJobType,

        Set<String> skills,

        String sourceFilename

) {

    public CandidateDraft {

        skills = skills == null
                ? Set.of()
                : Set.copyOf(skills);
    }
}
