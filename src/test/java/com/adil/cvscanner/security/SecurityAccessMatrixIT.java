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

    /*
     * ============================================================
     * TEST 1
     * PUBLIC HEALTH
     * ============================================================
     */

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

    /*
     * ============================================================
     * TEST 2
     * ANONYMOUS METRICS
     * ============================================================
     */

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

    /*
     * ============================================================
     * TEST 3
     * RECRUITER CANNOT ACCESS METRICS
     * ============================================================
     */

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

    /*
     * ============================================================
     * TEST 4
     * ADMIN CAN ACCESS METRICS
     * ============================================================
     */

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

    /*
     * ============================================================
     * TEST 5
     * ANONYMOUS APPLICATION API
     * ============================================================
     */

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

    /*
     * ============================================================
     * TEST 6
     * RECRUITER APPLICATION API
     * ============================================================
     */

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

    /*
     * ============================================================
     * TEST 7
     * ADMIN APPLICATION API
     * ============================================================
     */

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

    /*
     * ============================================================
     * TEST 8
     * UNKNOWN AUTHORITY
     * ============================================================
     */

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

    /*
     * ============================================================
     * TEST 9
     * DEFAULT DENY - NON API INTERNAL ROUTE
     * ============================================================
     */

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

    /*
     * ============================================================
     * TEST 10
     * UNKNOWN APPLICATION API ROUTE
     * ============================================================
     *
     * Köhnə:
     *
     * /api/v1/**
     *
     * matcher olsaydı recruiter security qatından
     * keçə bilərdi.
     *
     * Final explicit allowlist-də isə bu route
     * tanınmır və denyAll() tərəfindən bloklanır.
     */

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

    /*
     * ============================================================
     * TEST 11
     * UNSUPPORTED HTTP METHOD
     * ============================================================
     *
     * GET /api/v1/candidates icazəlidir.
     *
     * Amma:
     *
     * POST /api/v1/candidates
     *
     * application API contract-da yoxdur.
     *
     * Recruiter role-a sahib olsa belə
     * security səviyyəsində deny edilməlidir.
     */

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

    /*
     * ============================================================
     * SECURITY USERS
     * ============================================================
     */

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

    /*
     * ============================================================
     * TEMP STORAGE
     * ============================================================
     */

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