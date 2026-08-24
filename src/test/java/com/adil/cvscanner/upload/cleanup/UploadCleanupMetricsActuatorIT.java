package com.adil.cvscanner.upload.cleanup;

import com.adil.cvscanner.security.SecurityRoles;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class UploadCleanupMetricsActuatorIT {

    /*
     * ============================================================
     * TEMP STORAGE ROOT
     * ============================================================
     */

    private static final Path STORAGE_ROOT =
            createStorageRoot();

    /*
     * ============================================================
     * REAL POSTGRESQL
     * ============================================================
     */

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    "postgres:16-alpine"
            )
                    .withDatabaseName(
                            "cvscanner_cleanup_metrics_test"
                    )
                    .withUsername(
                            "test"
                    )
                    .withPassword(
                            "test"
                    );

    /*
     * ============================================================
     * DEPENDENCIES
     * ============================================================
     */

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UploadCleanupMetrics cleanupMetrics;

    /*
     * ============================================================
     * TEST CONFIGURATION
     * ============================================================
     */

    @DynamicPropertySource
    static void properties(
            DynamicPropertyRegistry registry
    ) {

        registry.add(
                "app.upload.storage-root",
                () -> STORAGE_ROOT.toString()
        );

        /*
         * Background scheduler test zamanı
         * metric values-i özbaşına dəyişməsin.
         */
        registry.add(
                "app.cleanup.scheduler-enabled",
                () -> "false"
        );

        registry.add(
                "app.batch.retry.delay",
                () -> "0ms"
        );

        /*
         * Actuator endpoint-ləri test context-də
         * expose edilir.
         */
        registry.add(
                "management.endpoints.web.exposure.include",
                () -> "health,metrics"
        );
    }

    /*
     * ============================================================
     * CLEANUP METRICS THROUGH REAL HTTP CONTRACT
     * ============================================================
     */

    @Test
    void shouldExposeCleanupOperationalMetricsThroughActuator()
            throws Exception {

        /*
         * ========================================================
         * ARRANGE METRICS
         * ========================================================
         */

        cleanupMetrics.recordCompletedRun(
                new UploadCleanupRunResult(
                        4,
                        3,
                        1,
                        0
                ),
                Duration.ofMillis(
                        250
                )
        );

        cleanupMetrics.recordDistributedLockAcquired();

        cleanupMetrics.recordDistributedLockContended();

        cleanupMetrics.recordSchedulerAttempt();

        cleanupMetrics.recordSchedulerLocalSkip();

        cleanupMetrics.recordSchedulerFailure();

        /*
         * ========================================================
         * METRIC LIST
         * ========================================================
         *
         * /actuator/metrics artıq ADMIN-only-dir.
         *
         * Buna görə authentication birbaşa request
         * üzərində verilir.
         */

        mockMvc.perform(
                        get(
                                "/actuator/metrics"
                        )
                                .with(
                                        adminUser()
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.names"
                        ).value(
                                hasItems(
                                        UploadCleanupMetrics.RUNS,
                                        UploadCleanupMetrics.SELECTED,
                                        UploadCleanupMetrics.DELETED,
                                        UploadCleanupMetrics.ALREADY_ABSENT,
                                        UploadCleanupMetrics.LOCK_ACQUIRED,
                                        UploadCleanupMetrics.LOCK_CONTENDED,
                                        UploadCleanupMetrics.SCHEDULER_ATTEMPTS,
                                        UploadCleanupMetrics.SCHEDULER_LOCAL_SKIPS,
                                        UploadCleanupMetrics.SCHEDULER_FAILURES
                                )
                        )
                );

        /*
         * ========================================================
         * CLEANUP METRICS
         * ========================================================
         */

        assertCounter(
                UploadCleanupMetrics.RUNS,
                1.0
        );

        assertCounter(
                UploadCleanupMetrics.SELECTED,
                4.0
        );

        assertCounter(
                UploadCleanupMetrics.DELETED,
                3.0
        );

        assertCounter(
                UploadCleanupMetrics.ALREADY_ABSENT,
                1.0
        );

        /*
         * ========================================================
         * DISTRIBUTED LOCK
         * ========================================================
         */

        assertCounter(
                UploadCleanupMetrics.LOCK_ACQUIRED,
                1.0
        );

        assertCounter(
                UploadCleanupMetrics.LOCK_CONTENDED,
                1.0
        );

        /*
         * ========================================================
         * SCHEDULER
         * ========================================================
         */

        assertCounter(
                UploadCleanupMetrics.SCHEDULER_ATTEMPTS,
                1.0
        );

        assertCounter(
                UploadCleanupMetrics.SCHEDULER_LOCAL_SKIPS,
                1.0
        );

        assertCounter(
                UploadCleanupMetrics.SCHEDULER_FAILURES,
                1.0
        );
    }

    /*
     * ============================================================
     * REAL HTTP COUNTER ASSERTION
     * ============================================================
     *
     * Burada da hər request ADMIN authentication almalıdır.
     *
     * Əks halda ilk /actuator/metrics request düzəlsə belə
     * individual:
     *
     * /actuator/metrics/{metricName}
     *
     * request-ləri 401 qaytarardı.
     */

    private void assertCounter(
            String metricName,
            double expectedValue
    ) throws Exception {

        mockMvc.perform(
                        get(
                                "/actuator/metrics/{metricName}",
                                metricName
                        )
                                .with(
                                        adminUser()
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.name"
                        ).value(
                                metricName
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.measurements[0].statistic"
                        ).value(
                                "COUNT"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.measurements[0].value"
                        ).value(
                                expectedValue
                        )
                );
    }

    /*
     * ============================================================
     * ADMIN AUTHENTICATION
     * ============================================================
     *
     * SecurityConfiguration:
     *
     * hasAuthority("ROLE_ADMIN")
     *
     * Test:
     *
     * ROLE_ADMIN
     *
     * Explicit match.
     */

    private RequestPostProcessor adminUser() {

        return user(
                "admin"
        )
                .authorities(
                        new SimpleGrantedAuthority(
                                SecurityRoles.ROLE_ADMIN
                        )
                );
    }

    /*
     * ============================================================
     * TEMP STORAGE FACTORY
     * ============================================================
     */

    private static Path createStorageRoot() {

        try {

            return Files.createTempDirectory(
                    "cvscanner-cleanup-metrics-actuator-it-"
            );

        } catch (
                IOException exception
        ) {

            throw new ExceptionInInitializerError(
                    exception
            );
        }
    }
}