package com.adil.cvscanner.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

public final class SecurityTestUsers {

    private SecurityTestUsers() {
        throw new IllegalStateException("Utility class");
    }

    public static RequestPostProcessor recruiter() {
        return user("recruiter-test")
                .authorities(
                        new SimpleGrantedAuthority(
                                SecurityRoles.ROLE_RECRUITER
                        )
                );
    }

    public static RequestPostProcessor admin() {
        return user("admin-test")
                .authorities(
                        new SimpleGrantedAuthority(
                                SecurityRoles.ROLE_ADMIN
                        )
                );
    }

    public static RequestPostProcessor unknownRole() {
        return user("unknown-test")
                .authorities(
                        new SimpleGrantedAuthority(
                                "ROLE_UNKNOWN"
                        )
                );
    }
}
