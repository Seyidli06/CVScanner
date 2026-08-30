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

    





    @BeforeEach
    void setUp() {

        



        databaseHealthIndicator.markUp();
    }

    























    @Test
    void shouldRemoveApplicationFromReadinessWithoutFailingLiveness()
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

        





        databaseHealthIndicator.markDown();

        
















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

        





        databaseHealthIndicator.markUp();

        





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