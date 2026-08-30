package com.adil.cvscanner.candidate.api;

import com.adil.cvscanner.candidate.application.CandidateQueryService;
import com.adil.cvscanner.candidate.application.CandidateSearchCriteria;
import com.adil.cvscanner.candidate.domain.JobType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/v1/candidates")
@SecurityRequirement(
        name = "bearerAuth"
)
public class CandidateController {

    private final CandidateQueryService candidateQueryService;

    public CandidateController(
            CandidateQueryService candidateQueryService
    ) {
        this.candidateQueryService =
                candidateQueryService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<CandidateResponse>>
    searchCandidates(

            @RequestParam(required = false)
            UUID uploadId,

            @RequestParam(required = false)
            String skill,

            @RequestParam(required = false)
            String location,

            @RequestParam(required = false)
            JobType jobType,

            @RequestParam(required = false)
            Integer minExperience,

            @RequestParam(required = false)
            Integer maxExperience,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "20")
            int size,

            @RequestParam(defaultValue = "fullName")
            String sortBy,

            @RequestParam(defaultValue = "asc")
            String direction
    ) {

        CandidateSearchCriteria criteria =
                new CandidateSearchCriteria(
                        uploadId,
                        skill,
                        location,
                        jobType,
                        minExperience,
                        maxExperience
                );

        PageResponse<CandidateResponse> response =
                candidateQueryService.search(
                        criteria,
                        page,
                        size,
                        sortBy,
                        direction
                );

        return ResponseEntity.ok(
                response
        );
    }
}
