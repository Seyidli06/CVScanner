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
