package com.adil.cvscanner.security;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(
        JwtResourceServerIT.TestJwtConfiguration.class
)
class JwtResourceServerIT {

    





    private static final String ISSUER =
            "https://issuer.cvscanner.test";

    














    private static final KeyPair KEY_PAIR =
            generateKeyPair();

    



    private static final KeyPair ATTACKER_KEY_PAIR =
            generateKeyPair();

    





    private static final Path STORAGE_ROOT =
            createStorageRoot();

    





    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    "postgres:16-alpine"
            )
                    .withDatabaseName(
                            "cvscanner_jwt_security_test"
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

        



        registry.add(
                "security.jwt.issuer-uri",
                () -> ISSUER
        );

        







        registry.add(
                "security.jwt.jwk-set-uri",
                () -> "https://unused.cvscanner.test/jwks"
        );

        registry.add(
                "security.jwt.roles-claim",
                () -> "roles"
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
                        status().isOk()
                );
    }

    







    @Test
    void shouldReturnUnauthorizedWhenBearerTokenIsMissing()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/actuator/metrics"
                        )
                )
                .andExpect(
                        status().isUnauthorized()
                );
    }

    







    @Test
    void shouldAllowValidAdminJwt()
            throws Exception {

        String token =
                createToken(
                        KEY_PAIR,
                        ISSUER,
                        "admin-user",
                        List.of(
                                SecurityRoles.ADMIN
                        ),
                        Instant.now()
                                .plusSeconds(
                                        300
                                )
                );

        mockMvc.perform(
                        get(
                                "/actuator/metrics"
                        )
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                )
                .andExpect(
                        status().isOk()
                );
    }

    







    @Test
    void shouldReturnForbiddenForRecruiterOnAdminEndpoint()
            throws Exception {

        String token =
                createToken(
                        KEY_PAIR,
                        ISSUER,
                        "recruiter-user",
                        List.of(
                                SecurityRoles.RECRUITER
                        ),
                        Instant.now()
                                .plusSeconds(
                                        300
                                )
                );

        mockMvc.perform(
                        get(
                                "/actuator/metrics"
                        )
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                )
                .andExpect(
                        status().isForbidden()
                );
    }

    







    @Test
    void shouldRejectUnknownRoleFromAdminEndpoint()
            throws Exception {

        String token =
                createToken(
                        KEY_PAIR,
                        ISSUER,
                        "unknown-role-user",
                        List.of(
                                "SUPER_ADMIN"
                        ),
                        Instant.now()
                                .plusSeconds(
                                        300
                                )
                );

        mockMvc.perform(
                        get(
                                "/actuator/metrics"
                        )
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                )
                .andExpect(
                        status().isForbidden()
                );
    }

    







    @Test
    void shouldRejectTokenWithWrongIssuer()
            throws Exception {

        String token =
                createToken(
                        KEY_PAIR,
                        "https://attacker.example.test",
                        "admin-user",
                        List.of(
                                SecurityRoles.ADMIN
                        ),
                        Instant.now()
                                .plusSeconds(
                                        300
                                )
                );

        mockMvc.perform(
                        get(
                                "/actuator/metrics"
                        )
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                )
                .andExpect(
                        status().isUnauthorized()
                );
    }

    







    @Test
    void shouldRejectTokenSignedByUnknownKey()
            throws Exception {

        






        String token =
                createToken(
                        ATTACKER_KEY_PAIR,
                        ISSUER,
                        "admin-user",
                        List.of(
                                SecurityRoles.ADMIN
                        ),
                        Instant.now()
                                .plusSeconds(
                                        300
                                )
                );

        mockMvc.perform(
                        get(
                                "/actuator/metrics"
                        )
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                )
                .andExpect(
                        status().isUnauthorized()
                );
    }

    







    @Test
    void shouldRejectExpiredToken()
            throws Exception {

        String token =
                createToken(
                        KEY_PAIR,
                        ISSUER,
                        "admin-user",
                        List.of(
                                SecurityRoles.ADMIN
                        ),

                        



                        Instant.now()
                                .minusSeconds(
                                        60
                                )
                );

        mockMvc.perform(
                        get(
                                "/actuator/metrics"
                        )
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                )
                .andExpect(
                        status().isUnauthorized()
                );
    }

    

















    @TestConfiguration
    static class TestJwtConfiguration {

        @Bean
        @Primary
        JwtDecoder testJwtDecoder() {

            NimbusJwtDecoder decoder =
                    NimbusJwtDecoder
                            .withPublicKey(
                                    (RSAPublicKey)
                                            KEY_PAIR
                                                    .getPublic()
                            )
                            .build();

            decoder.setJwtValidator(
                    JwtValidators
                            .createDefaultWithIssuer(
                                    ISSUER
                            )
            );

            return decoder;
        }
    }

    





    private static String createToken(
            KeyPair signingKeyPair,
            String issuer,
            String subject,
            List<String> roles,
            Instant expiration
    ) {

        try {

            Instant now =
                    Instant.now();

            JWTClaimsSet claims =
                    new JWTClaimsSet
                            .Builder()
                            .issuer(
                                    issuer
                            )
                            .subject(
                                    subject
                            )
                            .issueTime(
                                    Date.from(
                                            now
                                    )
                            )
                            .notBeforeTime(
                                    Date.from(
                                            now.minusSeconds(
                                                    5
                                            )
                                    )
                            )
                            .expirationTime(
                                    Date.from(
                                            expiration
                                    )
                            )
                            .claim(
                                    "roles",
                                    roles
                            )
                            .build();

            JWSHeader header =
                    new JWSHeader
                            .Builder(
                            JWSAlgorithm.RS256
                    )
                            .type(
                                    JOSEObjectType.JWT
                            )
                            .build();

            SignedJWT signedJwt =
                    new SignedJWT(
                            header,
                            claims
                    );

            signedJwt.sign(
                    new RSASSASigner(
                            (RSAPrivateKey)
                                    signingKeyPair
                                            .getPrivate()
                    )
            );

            return signedJwt.serialize();

        } catch (
                Exception exception
        ) {

            throw new IllegalStateException(
                    "Failed to create signed JWT for integration test",
                    exception
            );
        }
    }

    





    private static KeyPair generateKeyPair() {

        try {

            KeyPairGenerator generator =
                    KeyPairGenerator
                            .getInstance(
                                    "RSA"
                            );

            generator.initialize(
                    2048
            );

            return generator
                    .generateKeyPair();

        } catch (
                Exception exception
        ) {

            throw new ExceptionInInitializerError(
                    exception
            );
        }
    }

    





    private static Path createStorageRoot() {

        try {

            return Files.createTempDirectory(
                    "cvscanner-jwt-resource-server-it-"
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