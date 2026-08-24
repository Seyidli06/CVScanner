package com.adil.cvscanner.monitoring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(
        ReadinessIsolationIT.DatabaseHealthTestConfiguration.class
)
class ReadinessIsolationIT {

    /*
     * ============================================================
     * REAL POSTGRESQL
     * ============================================================
     *
     * Application context:
     *
     * Flyway
     * JPA
     * repositories
     *
     * real PostgreSQL ilə başlayır.
     *
     * Yəni test fake application context deyil.
     *
     * Sadəcə db health contributor-u test üçün
     * idarə edilən implementation ilə əvəz olunur.
     */

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    "postgres:16-alpine"
            )
                    .withDatabaseName(
                            "cvscanner_readiness_test"
                    )
                    .withUsername(
                            "test"
                    )
                    .withPassword(
                            "test"
                    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ControllableDatabaseHealthIndicator
            databaseHealthIndicator;

    /*
     * ============================================================
     * RESET
     * ============================================================
     */

    @BeforeEach
    void setUp() {

        /*
         * Hər test həmişə healthy vəziyyətdən
         * başlayır.
         */
        databaseHealthIndicator.markUp();
    }

    /*
     * ============================================================
     * MAIN TEST
     * ============================================================
     *
     * İstədiyimiz davranış:
     *
     * DB UP
     *
     * /livez  -> 200 UP
     * /readyz -> 200 UP
     *
     *
     * DB DOWN
     *
     * /livez  -> 200 UP
     * /readyz -> 503 DOWN
     *
     *
     * DB RECOVERS
     *
     * /readyz -> 200 UP
     */

    @Test
    void shouldRemoveApplicationFromReadinessWithoutFailingLiveness()
            throws Exception {

        /*
         * =====================================================
         * 1. INITIAL STATE
         * =====================================================
         *
         * Database health = UP.
         */

        mockMvc.perform(
                        get(
                                "/livez"
                        )
                )
                .andExpect(
                        status()
                                .isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.status"
                        ).value(
                                "UP"
                        )
                );

        mockMvc.perform(
                        get(
                                "/readyz"
                        )
                )
                .andExpect(
                        status()
                                .isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.status"
                        ).value(
                                "UP"
                        )
                );

        /*
         * =====================================================
         * 2. SIMULATE DATABASE FAILURE
         * =====================================================
         */

        databaseHealthIndicator.markDown();

        /*
         * =====================================================
         * 3. LIVENESS MUST REMAIN UP
         * =====================================================
         *
         * DB external dependency-dir.
         *
         * Database-in düşməsi:
         *
         * "JVM/application öldü"
         *
         * demək deyil.
         *
         * Ona görə Kubernetes application-ı
         * restart etməməlidir.
         */

        mockMvc.perform(
                        get(
                                "/actuator/health/liveness"
                        )
                )
                .andExpect(
                        status()
                                .isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.status"
                        ).value(
                                "UP"
                        )
                );

        mockMvc.perform(
                        get(
                                "/livez"
                        )
                )
                .andExpect(
                        status()
                                .isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.status"
                        ).value(
                                "UP"
                        )
                );

        /*
         * =====================================================
         * 4. READINESS MUST BECOME DOWN
         * =====================================================
         *
         * application.yaml:
         *
         * readiness:
         *   include:
         *     readinessState,db
         *
         *
         * db = DOWN
         *
         * therefore:
         *
         * readiness = DOWN
         *
         * Spring Boot default HTTP mapping:
         *
         * DOWN -> 503
         */

        mockMvc.perform(
                        get(
                                "/actuator/health/readiness"
                        )
                )
                .andExpect(
                        status()
                                .isServiceUnavailable()
                )
                .andExpect(
                        jsonPath(
                                "$.status"
                        ).value(
                                "DOWN"
                        )
                );

        mockMvc.perform(
                        get(
                                "/readyz"
                        )
                )
                .andExpect(
                        status()
                                .isServiceUnavailable()
                )
                .andExpect(
                        jsonPath(
                                "$.status"
                        ).value(
                                "DOWN"
                        )
                );

        /*
         * =====================================================
         * 5. SIMULATE DATABASE RECOVERY
         * =====================================================
         */

        databaseHealthIndicator.markUp();

        /*
         * Readiness avtomatik bərpa olunmalıdır.
         *
         * Application restart lazım deyil.
         */

        mockMvc.perform(
                        get(
                                "/actuator/health/readiness"
                        )
                )
                .andExpect(
                        status()
                                .isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.status"
                        ).value(
                                "UP"
                        )
                );

        mockMvc.perform(
                        get(
                                "/readyz"
                        )
                )
                .andExpect(
                        status()
                                .isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.status"
                        ).value(
                                "UP"
                        )
                );
    }

    /*
     * ============================================================
     * TEST CONFIGURATION
     * ============================================================
     *
     * Production source-a heç nə əlavə etmirik.
     *
     * Bu bean yalnız bu integration test
     * context-ində mövcuddur.
     *
     *
     * Bean name çox vacibdir:
     *
     * dbHealthIndicator
     *
     * Spring Boot contributor ID:
     *
     * db
     *
     *
     * application.yaml:
     *
     * readiness:
     *   include: readinessState,db
     *
     * ilə buna görə uyğunlaşır.
     */

    @TestConfiguration(
            proxyBeanMethods = false
    )
    static class DatabaseHealthTestConfiguration {

        @Bean("dbHealthIndicator")
        ControllableDatabaseHealthIndicator
        dbHealthIndicator() {

            return new ControllableDatabaseHealthIndicator();
        }
    }

    /*
     * ============================================================
     * CONTROLLABLE DB HEALTH
     * ============================================================
     */

    static class ControllableDatabaseHealthIndicator
            implements HealthIndicator {

        private final AtomicBoolean available =
                new AtomicBoolean(
                        true
                );

        @Override
        public Health health() {

            if (
                    available.get()
            ) {

                return Health
                        .up()
                        .build();
            }

            return Health
                    .down()
                    .build();
        }

        void markUp() {

            available.set(
                    true
            );
        }

        void markDown() {

            available.set(
                    false
            );
        }
    }
}