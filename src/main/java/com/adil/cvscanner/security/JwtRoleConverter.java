package com.adil.cvscanner.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

@Component
public class JwtRoleConverter
        implements Converter<
        Jwt,
        Collection<GrantedAuthority>
        > {

    private final JwtSecurityProperties properties;

    public JwtRoleConverter(
            JwtSecurityProperties properties
    ) {

        this.properties =
                properties;
    }

    @Override
    public Collection<GrantedAuthority> convert(
            Jwt jwt
    ) {

        Object rawClaim =
                jwt.getClaim(
                        properties
                                .getRolesClaim()
                );

        if (
                !(rawClaim instanceof Collection<?> roles)
        ) {

            return List.of();
        }

        return roles
                .stream()
                .filter(
                        String.class::isInstance
                )
                .map(
                        String.class::cast
                )
                .map(
                        String::trim
                )
                .filter(
                        role ->
                                !role.isBlank()
                )
                .map(
                        role ->
                                role.toUpperCase(
                                        Locale.ROOT
                                )
                )
                




                .filter(
                        role ->
                                role.equals(
                                        SecurityRoles.RECRUITER
                                )
                                        ||
                                        role.equals(
                                                SecurityRoles.ADMIN
                                        )
                )
                .distinct()
                .map(
                        role ->
                                new SimpleGrantedAuthority(
                                        "ROLE_" + role
                                )
                )
                .map(
                        GrantedAuthority.class::cast
                )
                .toList();
    }
}