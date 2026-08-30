package com.adil.cvscanner.security;

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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SecurityAccessMatrixIT {

    private static final Path STORAGE_ROOT =
            createStorageRoot();

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    "postgres:16-alpine"
            )
                    .withDatabaseName(
                            "cvscanner_security_test"
                    )
                    .withUsername(
                            "test"
                    )
                    .withPassword(
                            "test"
                    );

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void properties(
            DynamicPropertyRegistry registry
    ) {

        registry.add(
                "app.upload.storage-root",
                () -> STORAGE_ROOT.toString()
        );

        registry.add(
                "app.cleanup.scheduler-enabled",
                () -> "false"
        );

        registry.add(
                "app.batch.retry.delay",
                () -> "0ms"
        );

        registry.add(
                "management.endpoints.web.exposure.include",
                () -> "health,metrics"
        );
    }

    






    @Test
    void shouldAllowAnonymousHealthRequest()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/actuator/health"
                        )
                )
                .andExpect(
                        status()
                                .isOk()
                );
    }

    






    @Test
    void shouldRejectAnonymousMetricsRequest()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/actuator/metrics"
                        )
                )
                .andExpect(
                        status()
                                .isUnauthorized()
                );
    }

    






    @Test
    void shouldRejectRecruiterFromMetrics()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/actuator/metrics"
                        )
                                .with(
                                        recruiterUser()
                                )
                )
                .andExpect(
                        status()
                                .isForbidden()
                );
    }

    






    @Test
    void shouldAllowAdminToReadMetrics()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/actuator/metrics"
                        )
                                .with(
                                        adminUser()
                                )
                )
                .andExpect(
                        status()
                                .isOk()
                );
    }

    






    @Test
    void shouldRejectAnonymousApplicationApiRequest()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/v1/candidates"
                        )
                )
                .andExpect(
                        status()
                                .isUnauthorized()
                );
    }

    






    @Test
    void shouldAllowRecruiterToUseApplicationApi()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/v1/candidates"
                        )
                                .with(
                                        recruiterUser()
                                )
                )
                .andExpect(
                        status()
                                .isOk()
                );
    }

    






    @Test
    void shouldAllowAdminToUseApplicationApi()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/v1/candidates"
                        )
                                .with(
                                        adminUser()
                                )
                )
                .andExpect(
                        status()
                                .isOk()
                );
    }

    






    @Test
    void shouldRejectUnknownRoleFromApplicationApi()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/v1/candidates"
                        )
                                .with(
                                        user(
                                                "unknown-user"
                                        )
                                                .authorities(
                                                        new SimpleGrantedAuthority(
                                                                "ROLE_SUPER_ADMIN"
                                                        )
                                                )
                                )
                )
                .andExpect(
                        status()
                                .isForbidden()
                );
    }

    






    @Test
    void shouldDenyUnknownRouteEvenForAdmin()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/internal/something-dangerous"
                        )
                                .with(
                                        adminUser()
                                )
                )
                .andExpect(
                        status()
                                .isForbidden()
                );
    }

    
















    @Test
    void shouldDenyUnknownApplicationRouteForRecruiter()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/v1/unknown"
                        )
                                .with(
                                        recruiterUser()
                                )
                )
                .andExpect(
                        status()
                                .isForbidden()
                );
    }

    

















    @Test
    void shouldDenyUnsupportedApplicationMethodForRecruiter()
            throws Exception {

        mockMvc.perform(
                        post(
                                "/api/v1/candidates"
                        )
                                .with(
                                        recruiterUser()
                                )
                )
                .andExpect(
                        status()
                                .isForbidden()
                );
    }

    





    private RequestPostProcessor recruiterUser() {

        return user(
                "recruiter"
        )
                .authorities(
                        new SimpleGrantedAuthority(
                                SecurityRoles.ROLE_RECRUITER
                        )
                );
    }

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

    





    private static Path createStorageRoot() {

        try {

            return Files.createTempDirectory(
                    "cvscanner-security-it-"
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