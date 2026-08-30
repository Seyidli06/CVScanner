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

    private static final String CHUNK_LOGGER_NAME =
            "org.springframework.batch.core.step.item.ChunkOrientedStep";

    @Test
    void shouldDisableUnsafeChunkFrameworkLogger() {

        Logger logger =
                LoggerFactory.getLogger(
                        CHUNK_LOGGER_NAME
                );

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
