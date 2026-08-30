package com.adil.cvscanner.candidate.api;

import com.adil.cvscanner.candidate.application.CandidateSearchCriteria;
import com.adil.cvscanner.candidate.application.CandidateXlsxExportService;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/candidates")
@SecurityRequirement(name = "bearerAuth")
public class CandidateXlsxExportController {

    private static final MediaType XLSX_MEDIA_TYPE =
            MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            );

    private final CandidateXlsxExportService candidateXlsxExportService;

    public CandidateXlsxExportController(
            CandidateXlsxExportService candidateXlsxExportService
    ) {
        this.candidateXlsxExportService =
                candidateXlsxExportService;
    }

    @GetMapping(
            value = "/export.xlsx",
            produces =
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )
    public ResponseEntity<StreamingResponseBody> exportXlsx(
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

        candidateXlsxExportService.validateRequest(
                criteria,
                sortBy,
                direction
        );

        StreamingResponseBody body =
                outputStream ->
                        candidateXlsxExportService.writeXlsx(
                                outputStream,
                                criteria,
                                sortBy,
                                direction
                        );

        return ResponseEntity
                .ok()
                .contentType(XLSX_MEDIA_TYPE)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"candidates.xlsx\""
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
