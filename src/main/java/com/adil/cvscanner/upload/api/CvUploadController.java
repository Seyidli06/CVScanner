package com.adil.cvscanner.upload.api;

import com.adil.cvscanner.upload.application.CvUploadQueryService;
import com.adil.cvscanner.upload.application.CvUploadWorkflowService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;

@RestController
@RequestMapping("/api/v1/uploads")
@SecurityRequirement(name = "bearerAuth")
public class CvUploadController {

    private final CvUploadWorkflowService uploadWorkflowService;
    private final CvUploadQueryService uploadQueryService;

    public CvUploadController(
            CvUploadWorkflowService uploadWorkflowService,
            CvUploadQueryService uploadQueryService
    ) {
        this.uploadWorkflowService =
                uploadWorkflowService;

        this.uploadQueryService =
                uploadQueryService;
    }

    @PostMapping(consumes = MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> upload(
            @RequestPart("file")
            MultipartFile file
    ) {

        UploadResponse response =
                uploadWorkflowService.uploadAndStartProcessing(
                        file
                );

        return ResponseEntity
                .accepted()
                .body(response);
    }

    @GetMapping("/{uploadId}")
    public ResponseEntity<UploadStatusResponse> getUploadStatus(
            @PathVariable
            UUID uploadId
    ) {

        UploadStatusResponse response =
                uploadQueryService.getUploadStatus(
                        uploadId
                );

        return ResponseEntity.ok(
                response
        );
    }
}
