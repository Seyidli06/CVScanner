package com.adil.cvscanner.processing.batch;

import com.adil.cvscanner.candidate.application.CandidateDraft;
import com.adil.cvscanner.upload.application.CvUploadStatusService;
import org.springframework.batch.core.listener.ChunkListener;
import org.springframework.batch.infrastructure.item.Chunk;

import java.nio.file.Path;
import java.util.UUID;

public class CvProcessingProgressListener
        implements ChunkListener<Path, CandidateDraft> {

    private final UUID uploadId;

    private final CvUploadStatusService uploadStatusService;

    public CvProcessingProgressListener(
            UUID uploadId,
            CvUploadStatusService uploadStatusService
    ) {

        this.uploadId =
                uploadId;

        this.uploadStatusService =
                uploadStatusService;
    }

    @Override
    public void afterChunk(
            Chunk<CandidateDraft> chunk
    ) {

        if (
                chunk == null
                        || chunk.isEmpty()
        ) {
            return;
        }

        uploadStatusService
                .recordProcessed(
                        uploadId,
                        chunk.size()
                );
    }

}
