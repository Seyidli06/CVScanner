package com.adil.cvscanner.upload.cleanup;

import com.adil.cvscanner.upload.domain.CvUpload;
import com.adil.cvscanner.upload.domain.UploadStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface UploadCleanupCandidateRepository
        extends Repository<CvUpload, UUID> {

    @Query("""
            select upload
            from CvUpload upload
            where upload.status in :statuses
              and upload.completedAt is not null
              and upload.completedAt <= :cutoff
              and not exists (
                    select cleanup.uploadId
                    from UploadStorageCleanupRecord cleanup
                    where cleanup.uploadId = upload.id
              )
            order by upload.completedAt asc, upload.id asc
            """)
    List<CvUpload> findCleanupCandidates(
            @Param("statuses")
            Collection<UploadStatus> statuses,

            @Param("cutoff")
            OffsetDateTime cutoff,

            Pageable pageable
    );
}
