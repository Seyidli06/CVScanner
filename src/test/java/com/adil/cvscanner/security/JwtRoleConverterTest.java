package com.adil.cvscanner.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtRoleConverterTest {

    private JwtRoleConverter converter;

    @BeforeEach
    void setUp() {

        JwtSecurityProperties properties =
                new JwtSecurityProperties();

        properties.setIssuerUri(
                "https://issuer.example.test"
        );

        properties.setJwkSetUri(
                "https://issuer.example.test/jwks"
        );

        properties.setRolesClaim(
                "roles"
        );

        converter =
                new JwtRoleConverter(
                        properties
                );
    }

    @Test
    void shouldConvertKnownRolesToSpringAuthorities() {

        Jwt jwt =
                jwt(
                        Map.of(
                                "roles",
                                List.of(
                                        "RECRUITER",
                                        "ADMIN"
                                )
                        )
                );

        assertThat(
                converter.convert(
                        jwt
                )
        )
                .extracting(
                        authority ->
                                authority.getAuthority()
                )
                .containsExactlyInAnyOrder(
                        SecurityRoles.ROLE_RECRUITER,
                        SecurityRoles.ROLE_ADMIN
                );
    }

    @Test
    void shouldNormalizeRoleCase() {

        Jwt jwt =
                jwt(
                        Map.of(
                                "roles",
                                List.of(
                                        "recruiter",
                                        "Admin"
                                )
                        )
                );

        assertThat(
                converter.convert(
                        jwt
                )
        )
                .extracting(
                        authority ->
                                authority.getAuthority()
                )
                .containsExactlyInAnyOrder(
                        SecurityRoles.ROLE_RECRUITER,
                        SecurityRoles.ROLE_ADMIN
                );
    }

    @Test
    void shouldIgnoreUnknownRoles() {

        Jwt jwt =
                jwt(
                        Map.of(
                                "roles",
                                List.of(
                                        "ROOT",
                                        "SUPER_ADMIN",
                                        "ADMIN"
                                )
                        )
                );

        assertThat(
                converter.convert(
                        jwt
                )
        )
                .extracting(
                        authority ->
                                authority.getAuthority()
                )
                .containsExactly(
                        SecurityRoles.ROLE_ADMIN
                );
    }

    @Test
    void shouldReturnNoAuthoritiesWhenRolesClaimMissing() {

        Jwt jwt =
                jwt(
                        Map.of(
                                "sub",
                                "user-123"
                        )
                );

        assertThat(
                converter.convert(
                        jwt
                )
        ).isEmpty();
    }

    @Test
    void shouldIgnoreNonStringRoleValues() {

        Jwt jwt =
                jwt(
                        Map.of(
                                "roles",
                                List.of(
                                        "ADMIN",
                                        123,
                                        true
                                )
                        )
                );

        assertThat(
                converter.convert(
                        jwt
                )
        )
                .extracting(
                        authority ->
                                authority.getAuthority()
                )
                .containsExactly(
                        SecurityRoles.ROLE_ADMIN
                );
    }

    private Jwt jwt(
            Map<String, Object> claims
    ) {

        Instant now =
                Instant.now();

        return new Jwt(
                "test-token",
                now,
                now.plusSeconds(
                        300
                ),
                Map.of(
                        "alg",
                        "RS256"
                ),
                claims
        );
    }
}