package com.adil.cvscanner.security;

import com.adil.cvscanner.ratelimit.RateLimitingFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectProvider<RateLimitingFilter> rateLimitingFilterProvider,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            JwtDecoder jwtDecoder
    ) throws Exception {

        http
                /*
                 * ============================================================
                 * CSRF
                 * ============================================================
                 *
                 * API stateless Bearer JWT authentication istifadə edir.
                 */

                .csrf(
                        csrf ->
                                csrf.disable()
                )

                /*
                 * ============================================================
                 * SESSION
                 * ============================================================
                 */

                .sessionManagement(
                        session ->
                                session.sessionCreationPolicy(
                                        SessionCreationPolicy.STATELESS
                                )
                )

                /*
                 * ============================================================
                 * AUTHORIZATION
                 * ============================================================
                 */

                .authorizeHttpRequests(
                        authorization ->
                                authorization

                                        /*
                                         * =================================================
                                         * PUBLIC HEALTH
                                         * =================================================
                                         *
                                         * Kubernetes / platform health probes
                                         * authentication tələb etməməlidir.
                                         */

                                        .requestMatchers(
                                                "/actuator/health",
                                                "/actuator/health/**",
                                                "/livez",
                                                "/readyz"
                                        )
                                        .permitAll()

                                        /*
                                         * =================================================
                                         * ADMIN-ONLY METRICS
                                         * =================================================
                                         *
                                         * Runtime/application metrics yalnız ADMIN
                                         * authority üçün açıqdır.
                                         */

                                        .requestMatchers(
                                                "/actuator/metrics",
                                                "/actuator/metrics/**"
                                        )
                                        .hasAuthority(
                                                SecurityRoles.ROLE_ADMIN
                                        )


                                        .requestMatchers(
                                                "/v3/api-docs/**",
                                                "/swagger-ui/**",
                                                "/swagger-ui.html"
                                        )
                                        .permitAll()

                                        /*
                                         * =================================================
                                         * CV UPLOAD
                                         * =================================================
                                         *
                                         * POST /api/v1/uploads
                                         *
                                         * ZIP upload başlanğıc endpoint-i.
                                         */

                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/v1/uploads"
                                        )
                                        .hasAnyAuthority(
                                                SecurityRoles.ROLE_RECRUITER,
                                                SecurityRoles.ROLE_ADMIN
                                        )

                                        /*
                                         * =================================================
                                         * UPLOAD STATUS
                                         * =================================================
                                         *
                                         * GET /api/v1/uploads/{uploadId}
                                         */

                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/v1/uploads/*"
                                        )
                                        .hasAnyAuthority(
                                                SecurityRoles.ROLE_RECRUITER,
                                                SecurityRoles.ROLE_ADMIN
                                        )

                                        /*
                                         * =================================================
                                         * PROCESSING FAILURES
                                         * =================================================
                                         *
                                         * GET
                                         * /api/v1/uploads/{uploadId}/failures
                                         */

                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/v1/uploads/*/failures"
                                        )
                                        .hasAnyAuthority(
                                                SecurityRoles.ROLE_RECRUITER,
                                                SecurityRoles.ROLE_ADMIN
                                        )

                                        /*
                                         * =================================================
                                         * CANDIDATE SEARCH
                                         * =================================================
                                         *
                                         * GET /api/v1/candidates
                                         */

                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/v1/candidates"
                                        )
                                        .hasAnyAuthority(
                                                SecurityRoles.ROLE_RECRUITER,
                                                SecurityRoles.ROLE_ADMIN
                                        )

                                        /*
                                         * =================================================
                                         * CSV EXPORT
                                         * =================================================
                                         */

                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/v1/candidates/export.csv"
                                        )
                                        .hasAnyAuthority(
                                                SecurityRoles.ROLE_RECRUITER,
                                                SecurityRoles.ROLE_ADMIN
                                        )

                                        /*
                                         * =================================================
                                         * XLSX EXPORT
                                         * =================================================
                                         */

                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/v1/candidates/export.xlsx"
                                        )
                                        .hasAnyAuthority(
                                                SecurityRoles.ROLE_RECRUITER,
                                                SecurityRoles.ROLE_ADMIN
                                        )

                                        /*
                                         * =================================================
                                         * DEFAULT DENY
                                         * =================================================
                                         *
                                         * Buraya düşən hər şey qadağandır.
                                         *
                                         * Nümunə:
                                         *
                                         * GET    /api/v1/unknown
                                         * POST   /api/v1/candidates
                                         * DELETE /api/v1/uploads/{id}
                                         * GET    /internal/...
                                         * GET    /actuator/env
                                         */

                                        .anyRequest()
                                        .denyAll()
                )

                /*
                 * ============================================================
                 * OAUTH2 RESOURCE SERVER
                 * ============================================================
                 */

                .oauth2ResourceServer(
                        resourceServer ->
                                resourceServer
                                        .jwt(
                                                jwt ->
                                                        jwt
                                                                .decoder(
                                                                        jwtDecoder
                                                                )
                                                                .jwtAuthenticationConverter(
                                                                        jwtAuthenticationConverter
                                                                )
                                        )
                );

        /*
         * ============================================================
         * DISTRIBUTED RATE LIMITING
         * ============================================================
         *
         * Filter AuthorizationFilter-dən SONRA işləyir.
         *
         * Beləliklə request əvvəlcə:
         *
         * 1. JWT authentication
         * 2. RBAC authorization
         * 3. Rate limiting
         * 4. Controller
         *
         * mərhələlərindən keçir.
         *
         * app.rate-limit.enabled=false olduqda bean yoxdur.
         * ObjectProvider səbəbindən security chain yenə problemsiz
         * qurulur.
         */

        rateLimitingFilterProvider.ifAvailable(
                rateLimitingFilter ->
                        http.addFilterAfter(
                                rateLimitingFilter,
                                AuthorizationFilter.class
                        )
        );

        return http.build();
    }

    /*
     * ============================================================
     * JWT DECODER
     * ============================================================
     */

    @Bean
    JwtDecoder jwtDecoder(
            JwtSecurityProperties properties
    ) {

        NimbusJwtDecoder decoder =
                NimbusJwtDecoder
                        .withJwkSetUri(
                                properties.getJwkSetUri()
                        )
                        .build();

        OAuth2TokenValidator<Jwt> validator =
                JwtValidators
                        .createDefaultWithIssuer(
                                properties.getIssuerUri()
                        );

        decoder.setJwtValidator(
                validator
        );

        return decoder;
    }

    /*
     * ============================================================
     * JWT AUTHENTICATION CONVERTER
     * ============================================================
     */

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter(
            JwtRoleConverter jwtRoleConverter
    ) {

        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(
                jwtRoleConverter
        );

        return converter;
    }
}