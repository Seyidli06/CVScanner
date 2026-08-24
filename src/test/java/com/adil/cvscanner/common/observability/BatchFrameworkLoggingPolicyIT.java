package com.adil.cvscanner.common.observability;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class BatchFrameworkLoggingPolicyIT {

    /*
     * ============================================================
     * REAL POSTGRESQL
     * ============================================================
     *
     * Test production application.yaml ilə real
     * Spring context başladır.
     *
     * Logging property-ni test daxilində override
     * etmirik.
     */
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    "postgres:16-alpine"
            )
                    .withDatabaseName(
                            "cvscanner_logging_policy_test"
                    )
                    .withUsername(
                            "test"
                    )
                    .withPassword(
                            "test"
                    );

    /*
     * ============================================================
     * TARGET FRAMEWORK LOGGER
     * ============================================================
     *
     * Real logs-da raw exception stacktrace-i
     * məhz bu class yazırdı:
     *
     * org.springframework.batch.core.step.item.ChunkOrientedStep
     */
    private static final String CHUNK_LOGGER_NAME =
            "org.springframework.batch.core.step.item.ChunkOrientedStep";

    /*
     * ============================================================
     * TEST
     * ============================================================
     */
    @Test
    void shouldDisableUnsafeChunkFrameworkLogger() {

        Logger logger =
                LoggerFactory.getLogger(
                        CHUNK_LOGGER_NAME
                );

        /*
         * OFF olduqda heç bir severity aktiv
         * olmamalıdır.
         */
        assertThat(
                logger.isErrorEnabled()
        ).isFalse();

        assertThat(
                logger.isWarnEnabled()
        ).isFalse();

        assertThat(
                logger.isInfoEnabled()
        ).isFalse();

        assertThat(
                logger.isDebugEnabled()
        ).isFalse();

        assertThat(
                logger.isTraceEnabled()
        ).isFalse();
    }
}