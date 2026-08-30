package com.adil.cvscanner.candidate.api;

import com.adil.cvscanner.candidate.application.CandidateCsvExportService;
import com.adil.cvscanner.candidate.application.CandidateSearchCriteria;
import com.adil.cvscanner.candidate.domain.JobType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/candidates")
@SecurityRequirement(name = "bearerAuth")
public class CandidateExportController {

    private static final MediaType CSV_MEDIA_TYPE =
            new MediaType(
                    "text",
                    "csv",
                    StandardCharsets.UTF_8
            );

    private final CandidateCsvExportService candidateCsvExportService;

    public CandidateExportController(
            CandidateCsvExportService candidateCsvExportService
    ) {
        this.candidateCsvExportService =
                candidateCsvExportService;
    }

    @GetMapping(
            value = "/export.csv",
            produces = "text/csv"
    )
    public ResponseEntity<StreamingResponseBody> exportCsv(
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

        candidateCsvExportService.validateRequest(
                criteria,
                sortBy,
                direction
        );

        StreamingResponseBody body =
                outputStream ->
                        candidateCsvExportService.writeCsv(
                                outputStream,
                                criteria,
                                sortBy,
                                direction
                        );

        return ResponseEntity
                .ok()
                .contentType(CSV_MEDIA_TYPE)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"candidates.csv\""
                )
                .header(
                        "X-Content-Type-Options",
                        "nosniff"
                )
                .cacheControl(
                        CacheControl.noStore()
                )
                .body(body);
    }
}
