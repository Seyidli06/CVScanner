package com.adil.cvscanner.upload.cleanup;

import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

@Component
public class PostgresUploadCleanupLock {

    /*
     * ============================================================
     * GLOBAL ADVISORY LOCK KEY
     * ============================================================
     */

    private static final long LOCK_KEY =
            0x435653434C45414EL;

    private static final String TRY_LOCK_SQL =
            """
            select pg_try_advisory_lock(?)
            """;

    private static final String UNLOCK_SQL =
            """
            select pg_advisory_unlock(?)
            """;

    private final DataSource dataSource;

    private final UploadCleanupMetrics cleanupMetrics;

    public PostgresUploadCleanupLock(
            DataSource dataSource,
            UploadCleanupMetrics cleanupMetrics
    ) {

        this.dataSource =
                Objects.requireNonNull(
                        dataSource,
                        "dataSource must not be null"
                );

        this.cleanupMetrics =
                Objects.requireNonNull(
                        cleanupMetrics,
                        "cleanupMetrics must not be null"
                );
    }

    /*
     * ============================================================
     * EXECUTE UNDER DISTRIBUTED LOCK
     * ============================================================
     */

    public <T> Optional<T> tryExecute(
            Supplier<T> action
    ) {

        Objects.requireNonNull(
                action,
                "action must not be null"
        );

        try (
                Connection connection =
                        dataSource.getConnection()
        ) {

            boolean acquired =
                    tryAcquire(
                            connection
                    );

            /*
             * ====================================================
             * ANOTHER INSTANCE OWNS THE LOCK
             * ====================================================
             */

            if (
                    !acquired
            ) {

                cleanupMetrics
                        .recordDistributedLockContended();

                return Optional.empty();
            }

            cleanupMetrics
                    .recordDistributedLockAcquired();

            RuntimeException actionRuntimeFailure =
                    null;

            Error actionError =
                    null;

            try {

                T result =
                        Objects.requireNonNull(
                                action.get(),
                                "cleanup action must not return null"
                        );

                return Optional.of(
                        result
                );

            } catch (
                    RuntimeException exception
            ) {

                actionRuntimeFailure =
                        exception;

                throw exception;

            } catch (
                    Error error
            ) {

                actionError =
                        error;

                throw error;

            } finally {

                try {

                    release(
                            connection
                    );

                } catch (
                        RuntimeException releaseFailure
                ) {

                    /*
                     * Action özü fail olubsa unlock failure
                     * original exception-ı mask etməməlidir.
                     */

                    if (
                            actionRuntimeFailure != null
                    ) {

                        actionRuntimeFailure
                                .addSuppressed(
                                        releaseFailure
                                );

                    } else if (
                            actionError != null
                    ) {

                        actionError
                                .addSuppressed(
                                        releaseFailure
                                );

                    } else {

                        throw releaseFailure;
                    }
                }
            }

        } catch (
                SQLException exception
        ) {

            throw new UploadCleanupLockException(
                    "Failed to access PostgreSQL cleanup lock",
                    exception
            );
        }
    }

    /*
     * ============================================================
     * ACQUIRE
     * ============================================================
     */

    private boolean tryAcquire(
            Connection connection
    ) {

        try (
                PreparedStatement statement =
                        connection.prepareStatement(
                                TRY_LOCK_SQL
                        )
        ) {

            statement.setLong(
                    1,
                    LOCK_KEY
            );

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {

                if (
                        !resultSet.next()
                ) {

                    throw new UploadCleanupLockException(
                            "PostgreSQL cleanup lock query returned no result"
                    );
                }

                return resultSet.getBoolean(
                        1
                );
            }

        } catch (
                SQLException exception
        ) {

            throw new UploadCleanupLockException(
                    "Failed to acquire PostgreSQL cleanup lock",
                    exception
            );
        }
    }

    /*
     * ============================================================
     * RELEASE
     * ============================================================
     */

    private void release(
            Connection connection
    ) {

        try (
                PreparedStatement statement =
                        connection.prepareStatement(
                                UNLOCK_SQL
                        )
        ) {

            statement.setLong(
                    1,
                    LOCK_KEY
            );

            try (
                    ResultSet resultSet =
                            statement.executeQuery()
            ) {

                if (
                        !resultSet.next()
                ) {

                    throw new UploadCleanupLockException(
                            "PostgreSQL cleanup unlock query returned no result"
                    );
                }

                boolean unlocked =
                        resultSet.getBoolean(
                                1
                        );

                if (
                        !unlocked
                ) {

                    throw new UploadCleanupLockException(
                            "PostgreSQL cleanup lock was not held by current session"
                    );
                }
            }

        } catch (
                SQLException exception
        ) {

            throw new UploadCleanupLockException(
                    "Failed to release PostgreSQL cleanup lock",
                    exception
            );
        }
    }
}