package com.adil.cvscanner.security;

public final class SecurityRoles {

    public static final String RECRUITER =
            "RECRUITER";

    public static final String ADMIN =
            "ADMIN";

    public static final String ROLE_RECRUITER =
            "ROLE_RECRUITER";

    public static final String ROLE_ADMIN =
            "ROLE_ADMIN";

    private SecurityRoles() {

        throw new IllegalStateException(
                "Utility class"
        );
    }
}
