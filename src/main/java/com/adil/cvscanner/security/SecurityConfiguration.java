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

                .csrf(
                        csrf ->
                                csrf.disable()
                )

                .sessionManagement(
                        session ->
                                session.sessionCreationPolicy(
                                        SessionCreationPolicy.STATELESS
                                )
                )

                .authorizeHttpRequests(
                        authorization ->
                                authorization

                                        .requestMatchers(
                                                "/actuator/health",
                                                "/actuator/health/**",
                                                "/livez",
                                                "/readyz"
                                        )
                                        .permitAll()

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

                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/v1/uploads"
                                        )
                                        .hasAnyAuthority(
                                                SecurityRoles.ROLE_RECRUITER,
                                                SecurityRoles.ROLE_ADMIN
                                        )

                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/v1/uploads/*"
                                        )
                                        .hasAnyAuthority(
                                                SecurityRoles.ROLE_RECRUITER,
                                                SecurityRoles.ROLE_ADMIN
                                        )

                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/v1/uploads/*/failures"
                                        )
                                        .hasAnyAuthority(
                                                SecurityRoles.ROLE_RECRUITER,
                                                SecurityRoles.ROLE_ADMIN
                                        )

                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/v1/candidates"
                                        )
                                        .hasAnyAuthority(
                                                SecurityRoles.ROLE_RECRUITER,
                                                SecurityRoles.ROLE_ADMIN
                                        )

                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/v1/candidates/export.csv"
                                        )
                                        .hasAnyAuthority(
                                                SecurityRoles.ROLE_RECRUITER,
                                                SecurityRoles.ROLE_ADMIN
                                        )

                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/v1/candidates/export.xlsx"
                                        )
                                        .hasAnyAuthority(
                                                SecurityRoles.ROLE_RECRUITER,
                                                SecurityRoles.ROLE_ADMIN
                                        )

                                        .anyRequest()
                                        .denyAll()
                )

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

        rateLimitingFilterProvider.ifAvailable(
                rateLimitingFilter ->
                        http.addFilterAfter(
                                rateLimitingFilter,
                                AuthorizationFilter.class
                        )
        );

        return http.build();
    }

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
