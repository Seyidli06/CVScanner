package com.adil.cvscanner.ratelimit;

import com.adil.cvscanner.security.SecurityRoles;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        properties = {
                "app.rate-limit.enabled=true",
                "app.rate-limit.fail-open=false",

                "app.rate-limit.redis-timeout=500ms",
                "app.rate-limit.key-prefix=cvscanner:test:rate-limit",

                "app.rate-limit.upload.capacity=2",
                "app.rate-limit.upload.refill-tokens=2",
                "app.rate-limit.upload.refill-period=1h",

                "app.rate-limit.read.capacity=2",
                "app.rate-limit.read.refill-tokens=2",
                "app.rate-limit.read.refill-period=1h",

                "app.rate-limit.export.capacity=1",
                "app.rate-limit.export.refill-tokens=1",
                "app.rate-limit.export.refill-period=1h",

                "security.jwt.issuer-uri=http://localhost/test-issuer",
                "security.jwt.jwk-set-uri=http://localhost/test-jwks",
                "security.jwt.roles-claim=roles"
        }
)
@AutoConfigureMockMvc
@Testcontainers
class RateLimitingIT {

    private static final int REDIS_PORT =
            6379;

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(
                    "postgres:16-alpine"
            )
                    .withDatabaseName(
                            "cvscanner"
                    )
                    .withUsername(
                            "cvscanner"
                    )
                    .withPassword(
                            "cvscanner"
                    );

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(
                    DockerImageName.parse(
                            "redis:7-alpine"
                    )
            )
                    .withExposedPorts(
                            REDIS_PORT
                    );

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void infrastructureProperties(
            DynamicPropertyRegistry registry
    ) {

        registry.add(
                "spring.datasource.url",
                POSTGRES::getJdbcUrl
        );

        registry.add(
                "spring.datasource.username",
                POSTGRES::getUsername
        );

        registry.add(
                "spring.datasource.password",
                POSTGRES::getPassword
        );

        registry.add(
                "app.rate-limit.redis-uri",
                () ->
                        "redis://"
                                + REDIS.getHost()
                                + ":"
                                + REDIS.getMappedPort(
                                REDIS_PORT
                        )
        );
    }

    /*
     * ============================================================
     * 429 CONTRACT
     * ============================================================
     */

    @Test
    void shouldReturn429WhenReadLimitIsExceeded()
            throws Exception {

        RequestPostProcessor recruiter =
                recruiter(
                        "read-limit-user"
                );

        mockMvc.perform(
                        get(
                                "/api/v1/candidates"
                        )
                                .with(
                                        recruiter
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        header().string(
                                RateLimitResponseWriter
                                        .RATE_LIMIT_REMAINING_HEADER,
                                "1"
                        )
                );

        mockMvc.perform(
                        get(
                                "/api/v1/candidates"
                        )
                                .with(
                                        recruiter
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        header().string(
                                RateLimitResponseWriter
                                        .RATE_LIMIT_REMAINING_HEADER,
                                "0"
                        )
                );

        mockMvc.perform(
                        get(
                                "/api/v1/candidates"
                        )
                                .with(
                                        recruiter
                                )
                )
                .andExpect(
                        status().isTooManyRequests()
                )
                .andExpect(
                        header().string(
                                RateLimitResponseWriter
                                        .RATE_LIMIT_REMAINING_HEADER,
                                "0"
                        )
                )
                .andExpect(
                        header().string(
                                HttpHeaders.RETRY_AFTER,
                                matchesPattern(
                                        "[1-9][0-9]*"
                                )
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.status"
                        )
                                .value(
                                        429
                                )
                )
                .andExpect(
                        jsonPath(
                                "$.error"
                        )
                                .value(
                                        "Too Many Requests"
                                )
                )
                .andExpect(
                        jsonPath(
                                "$.code"
                        )
                                .value(
                                        "RATE_LIMIT_EXCEEDED"
                                )
                )
                .andExpect(
                        jsonPath(
                                "$.message"
                        )
                                .value(
                                        "Too many requests. Please try again later."
                                )
                )
                .andExpect(
                        jsonPath(
                                "$.path"
                        )
                                .value(
                                        "/api/v1/candidates"
                                )
                )
                .andExpect(
                        jsonPath(
                                "$.timestamp"
                        )
                                .exists()
                );
    }

    /*
     * ============================================================
     * PRINCIPAL ISOLATION
     * ============================================================
     */

    @Test
    void shouldMaintainIndependentBucketsForDifferentUsers()
            throws Exception {

        RequestPostProcessor userA =
                recruiter(
                        "principal-a"
                );

        RequestPostProcessor userB =
                recruiter(
                        "principal-b"
                );

        mockMvc.perform(
                        get(
                                "/api/v1/candidates"
                        )
                                .with(
                                        userA
                                )
                )
                .andExpect(
                        status().isOk()
                );

        mockMvc.perform(
                        get(
                                "/api/v1/candidates"
                        )
                                .with(
                                        userA
                                )
                )
                .andExpect(
                        status().isOk()
                );

        mockMvc.perform(
                        get(
                                "/api/v1/candidates"
                        )
                                .with(
                                        userA
                                )
                )
                .andExpect(
                        status().isTooManyRequests()
                );

        mockMvc.perform(
                        get(
                                "/api/v1/candidates"
                        )
                                .with(
                                        userB
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        header().string(
                                RateLimitResponseWriter
                                        .RATE_LIMIT_REMAINING_HEADER,
                                "1"
                        )
                );
    }

    /*
     * ============================================================
     * POLICY ISOLATION
     * ============================================================
     */

    @Test
    void shouldKeepReadAndExportBucketsIndependent()
            throws Exception {

        RequestPostProcessor recruiter =
                recruiter(
                        "policy-isolation-user"
                );

        mockMvc.perform(
                        get(
                                "/api/v1/candidates"
                        )
                                .with(
                                        recruiter
                                )
                )
                .andExpect(
                        status().isOk()
                );

        mockMvc.perform(
                        get(
                                "/api/v1/candidates"
                        )
                                .with(
                                        recruiter
                                )
                )
                .andExpect(
                        status().isOk()
                );

        mockMvc.perform(
                        get(
                                "/api/v1/candidates"
                        )
                                .with(
                                        recruiter
                                )
                )
                .andExpect(
                        status().isTooManyRequests()
                );

        mockMvc.perform(
                        get(
                                "/api/v1/candidates/export.csv"
                        )
                                .with(
                                        recruiter
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        header().string(
                                RateLimitResponseWriter
                                        .RATE_LIMIT_REMAINING_HEADER,
                                "0"
                        )
                );

        mockMvc.perform(
                        get(
                                "/api/v1/candidates/export.csv"
                        )
                                .with(
                                        recruiter
                                )
                )
                .andExpect(
                        status().isTooManyRequests()
                );
    }

    /*
     * ============================================================
     * FORBIDDEN MUST NOT CONSUME
     * ============================================================
     */

    @Test
    void shouldNotConsumeRateLimitTokensForForbiddenRequest()
            throws Exception {

        String username =
                "role-switch-user";

        RequestPostProcessor forbiddenUser =
                user(
                        username
                )
                        .authorities(
                                new SimpleGrantedAuthority(
                                        "ROLE_UNKNOWN"
                                )
                        );

        mockMvc.perform(
                        get(
                                "/api/v1/candidates"
                        )
                                .with(
                                        forbiddenUser
                                )
                )
                .andExpect(
                        status().isForbidden()
                );

        mockMvc.perform(
                        get(
                                "/api/v1/candidates"
                        )
                                .with(
                                        recruiter(
                                                username
                                        )
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        header().string(
                                RateLimitResponseWriter
                                        .RATE_LIMIT_REMAINING_HEADER,
                                "1"
                        )
                );
    }

    /*
     * ============================================================
     * HEALTH MUST NOT BE RATE LIMITED
     * ============================================================
     */

    @Test
    void shouldNotRateLimitHealthEndpoint()
            throws Exception {

        for (
                int index = 0;
                index < 5;
                index++
        ) {

            mockMvc.perform(
                            get(
                                    "/actuator/health"
                            )
                    )
                    .andExpect(
                            status().isOk()
                    )
                    .andExpect(
                            header().doesNotExist(
                                    RateLimitResponseWriter
                                            .RATE_LIMIT_REMAINING_HEADER
                            )
                    );
        }
    }

    private RequestPostProcessor recruiter(
            String username
    ) {

        return user(
                username
        )
                .authorities(
                        new SimpleGrantedAuthority(
                                SecurityRoles.ROLE_RECRUITER
                        )
                );
    }
}