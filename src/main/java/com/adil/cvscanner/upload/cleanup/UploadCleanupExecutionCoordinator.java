package com.adil.cvscanner.upload.cleanup;

import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UploadCleanupExecutionCoordinator {

    private final PostgresUploadCleanupLock
            distributedLock;

    private final UploadStorageCleanupService
            cleanupService;

    public UploadCleanupExecutionCoordinator(
            PostgresUploadCleanupLock distributedLock,
            UploadStorageCleanupService cleanupService
    ) {

        this.distributedLock =
                distributedLock;

        this.cleanupService =
                cleanupService;
    }

    





    public UploadCleanupExecutionResult tryRunOnce() {

        Optional<UploadCleanupRunResult> result =
                distributedLock
                        .tryExecute(
                                cleanupService::runOnce
                        );

        if (
                result.isEmpty()
        ) {

            return UploadCleanupExecutionResult
                    .skipped();
        }

        return UploadCleanupExecutionResult
                .executed(
                        result.get()
                );
    }
}