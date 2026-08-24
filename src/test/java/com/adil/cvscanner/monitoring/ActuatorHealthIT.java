package com.adil.cvscanner.monitoring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ActuatorHealthIT {

    /*
     * ============================================================
     * REAL POSTGRESQL
     * ============================================================
     *
     * Readiness group:
     *
     * readinessState + db
     *
     * olduğu üçün real database istifadə edirik.
     *
     * localhost:5435 kimi developer-machine
     * dependency-si yoxdur.
     */

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    "postgres:16-alpine"
            )
                    .withDatabaseName(
                            "cvscanner_health_test"
                    )
                    .withUsername(
                            "test"
                    )
                    .withPassword(
                            "test"
                    );

    @Autowired
    private MockMvc mockMvc;

    /*
     * ============================================================
     * TEST 1
     * OVERALL HEALTH
     * ============================================================
     */

    @Test
    void shouldExposeOverallHealth()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/actuator/health"
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
     * TEST 2
     * INTERNAL DETAILS MUST NOT BE EXPOSED
     * ============================================================
     *
     * application.yaml:
     *
     * show-details: never
     * show-components: never
     *
     * Client yalnız:
     *
     * {
     *   "status": "UP"
     * }
     *
     * görməlidir.
     */

    @Test
    void shouldHideHealthComponentsAndDetails()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/actuator/health"
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
                )
                .andExpect(
                        jsonPath(
                                "$.components"
                        ).doesNotExist()
                )
                .andExpect(
                        jsonPath(
                                "$.details"
                        ).doesNotExist()
                );
    }

    /*
     * ============================================================
     * TEST 3
     * LIVENESS
     * ============================================================
     *
     * Liveness:
     *
     * JVM/application process yaşayır?
     *
     * DB burada criterion deyil.
     */

    @Test
    void shouldExposeLivenessProbe()
            throws Exception {

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
                )
                .andExpect(
                        jsonPath(
                                "$.components"
                        ).doesNotExist()
                )
                .andExpect(
                        jsonPath(
                                "$.details"
                        ).doesNotExist()
                );
    }

    /*
     * ============================================================
     * TEST 4
     * READINESS
     * ============================================================
     *
     * Readiness group:
     *
     * readinessState
     * +
     * db
     *
     * Real PostgreSQL UP olduğu üçün
     * readiness də UP olmalıdır.
     */

    @Test
    void shouldExposeReadinessProbeWithDatabaseAvailable()
            throws Exception {

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
                )
                .andExpect(
                        jsonPath(
                                "$.components"
                        ).doesNotExist()
                )
                .andExpect(
                        jsonPath(
                                "$.details"
                        ).doesNotExist()
                );
    }

    /*
     * ============================================================
     * TEST 5
     * SHORT LIVENESS PATH
     * ============================================================
     *
     * management.endpoint.health.probes
     *     .add-additional-paths=true
     *
     * nəticəsində:
     *
     * /livez
     */

    @Test
    void shouldExposeLivezAdditionalPath()
            throws Exception {

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
    }

    /*
     * ============================================================
     * TEST 6
     * SHORT READINESS PATH
     * ============================================================
     */

    @Test
    void shouldExposeReadyzAdditionalPath()
            throws Exception {

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
     * TEST 7
     * ENVIRONMENT ACTUATOR MUST NOT BE ANONYMOUSLY ACCESSIBLE
     * ============================================================
     *
     * Final security model:
     *
     * PUBLIC:
     *   /actuator/health/**
     *   /livez
     *   /readyz
     *
     * ADMIN:
     *   /actuator/metrics/**
     *
     * everything else:
     *   denied
     *
     * Buna görə anonymous:
     *
     * GET /actuator/env
     *
     * controller/MVC səviyyəsinə çatmadan
     * Spring Security tərəfindən 401 ilə
     * reject olunur.
     */

    @Test
    void shouldRejectAnonymousEnvironmentActuatorEndpoint()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/actuator/env"
                        )
                )
                .andExpect(
                        status()
                                .isUnauthorized()
                );
    }
}