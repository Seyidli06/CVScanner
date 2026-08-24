package com.adil.cvscanner.processing.api;

import com.adil.cvscanner.processing.application.ProcessingFailureQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(
        "/api/v1/uploads/{uploadId}/failures"
)
public class ProcessingFailureController {

    private final ProcessingFailureQueryService
            processingFailureQueryService;

    public ProcessingFailureController(
            ProcessingFailureQueryService
                    processingFailureQueryService
    ) {

        this.processingFailureQueryService =
                processingFailureQueryService;
    }

    /*
     * ============================================================
     * GET PROCESSING FAILURES
     * ============================================================
     *
     * GET
     *
     * /api/v1/uploads/{uploadId}/failures
     *
     *
     * Optional:
     *
     * ?page=0
     * &size=20
     */

    @GetMapping
    public ResponseEntity<ProcessingFailurePageResponse>
    getFailures(

            @PathVariable
            UUID uploadId,

            @RequestParam(
                    defaultValue = "0"
            )
            int page,

            @RequestParam(
                    defaultValue = "20"
            )
            int size
    ) {

        ProcessingFailurePageResponse response =
                processingFailureQueryService
                        .findByUpload(
                                uploadId,
                                page,
                                size
                        );

        return ResponseEntity.ok(
                response
        );
    }
}