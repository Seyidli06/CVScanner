package com.adil.cvscanner.ratelimit;

import com.adil.cvscanner.security.SecurityRoles;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        properties = {
                "app.rate-limit.enabled=true",

                


                "app.rate-limit.fail-open=true",

                "app.rate-limit.redis-timeout=100ms",
                "app.rate-limit.key-prefix=cvscanner:test:fail-open",

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
@DirtiesContext(
        classMode = DirtiesContext.ClassMode.AFTER_CLASS
)
class RateLimitFailOpenIT {

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

    @Test
    void shouldAllowRequestWhenRedisIsUnavailableAndFailOpenIsTrue()
            throws Exception {

        




        REDIS.stop();

        mockMvc.perform(
                        get(
                                "/api/v1/candidates"
                        )
                                .with(
                                        user(
                                                "fail-open-user"
                                        )
                                                .authorities(
                                                        new SimpleGrantedAuthority(
                                                                SecurityRoles
                                                                        .ROLE_RECRUITER
                                                        )
                                                )
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
