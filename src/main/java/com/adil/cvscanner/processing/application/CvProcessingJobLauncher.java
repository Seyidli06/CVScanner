package com.adil.cvscanner.processing.application;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobExecutionException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class CvProcessingJobLauncher {

    private final JobOperator jobOperator;

    private final Job cvProcessingJob;

    public CvProcessingJobLauncher(
            JobOperator jobOperator,
            Job cvProcessingJob
    ) {
        this.jobOperator =
                jobOperator;

        this.cvProcessingJob =
                cvProcessingJob;
    }

    public JobExecution launch(
            UUID uploadId
    ) {

        Objects.requireNonNull(
                uploadId,
                "uploadId must not be null"
        );

        JobParameters jobParameters =
                new JobParametersBuilder()
                        .addString(
                                "uploadId",
                                uploadId.toString()
                        )
                        .toJobParameters();

        try {

            return jobOperator.start(
                    cvProcessingJob,
                    jobParameters
            );

        } catch (
                JobExecutionException exception
        ) {

            throw new CvProcessingLaunchException(
                    uploadId,
                    "Failed to launch CV processing job for upload: "
                            + uploadId,
                    exception
            );
        }
    }
}