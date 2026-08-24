package com.adil.cvscanner.upload.cleanup;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(
        prefix = "app.cleanup"
)
public class UploadCleanupProperties {

    /*
     * ============================================================
     * CLEANUP BUSINESS SWITCH
     * ============================================================
     */

    private boolean enabled =
            true;

    /*
     * ============================================================
     * RETENTION
     * ============================================================
     */

    private Duration completedRetention =
            Duration.ofDays(
                    7
            );

    /*
     * ============================================================
     * MAX UPLOADS PER CLEANUP RUN
     * ============================================================
     */

    private int batchSize =
            100;

    /*
     * ============================================================
     * AUTOMATIC SCHEDULER SWITCH
     * ============================================================
     *
     * Destructive background operation olduğu üçün
     * default FALSE saxlayırıq.
     *
     * Manual cleanup service bundan asılı deyil.
     */

    private boolean schedulerEnabled =
            false;

    /*
     * ============================================================
     * DELAY BETWEEN RUNS
     * ============================================================
     *
     * fixedDelay semantics:
     *
     * cleanup bitir
     *      ↓
     * 1 saat gözlə
     *      ↓
     * növbəti cleanup
     */

    private Duration scheduleDelay =
            Duration.ofHours(
                    1
            );

    /*
     * ============================================================
     * APPLICATION START INITIAL DELAY
     * ============================================================
     *
     * App qalxan kimi filesystem cleanup başlamasın.
     */

    private Duration initialDelay =
            Duration.ofMinutes(
                    1
            );

    /*
     * ============================================================
     * VALIDATION
     * ============================================================
     */

    @PostConstruct
    void validate() {

        if (
                completedRetention == null
        ) {

            throw new IllegalStateException(
                    "app.cleanup.completed-retention is required"
            );
        }

        if (
                completedRetention.isZero()
                        ||
                        completedRetention.isNegative()
        ) {

            throw new IllegalStateException(
                    "app.cleanup.completed-retention must be positive"
            );
        }

        if (
                batchSize < 1
        ) {

            throw new IllegalStateException(
                    "app.cleanup.batch-size must be positive"
            );
        }

        if (
                batchSize > 1000
        ) {

            throw new IllegalStateException(
                    "app.cleanup.batch-size must not exceed 1000"
            );
        }

        if (
                scheduleDelay == null
        ) {

            throw new IllegalStateException(
                    "app.cleanup.schedule-delay is required"
            );
        }

        if (
                scheduleDelay.isZero()
                        ||
                        scheduleDelay.isNegative()
        ) {

            throw new IllegalStateException(
                    "app.cleanup.schedule-delay must be positive"
            );
        }

        if (
                initialDelay == null
        ) {

            throw new IllegalStateException(
                    "app.cleanup.initial-delay is required"
            );
        }

        if (
                initialDelay.isNegative()
        ) {

            throw new IllegalStateException(
                    "app.cleanup.initial-delay must not be negative"
            );
        }
    }

    /*
     * ============================================================
     * GETTERS / SETTERS
     * ============================================================
     */

    public boolean isEnabled() {

        return enabled;
    }

    public void setEnabled(
            boolean enabled
    ) {

        this.enabled =
                enabled;
    }

    public Duration getCompletedRetention() {

        return completedRetention;
    }

    public void setCompletedRetention(
            Duration completedRetention
    ) {

        this.completedRetention =
                completedRetention;
    }

    public int getBatchSize() {

        return batchSize;
    }

    public void setBatchSize(
            int batchSize
    ) {

        this.batchSize =
                batchSize;
    }

    public boolean isSchedulerEnabled() {

        return schedulerEnabled;
    }

    public void setSchedulerEnabled(
            boolean schedulerEnabled
    ) {

        this.schedulerEnabled =
                schedulerEnabled;
    }

    public Duration getScheduleDelay() {

        return scheduleDelay;
    }

    public void setScheduleDelay(
            Duration scheduleDelay
    ) {

        this.scheduleDelay =
                scheduleDelay;
    }

    public Duration getInitialDelay() {

        return initialDelay;
    }

    public void setInitialDelay(
            Duration initialDelay
    ) {

        this.initialDelay =
                initialDelay;
    }
}