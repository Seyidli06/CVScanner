package com.adil.cvscanner.security;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(
        prefix = "security.jwt"
)
public class JwtSecurityProperties {

    private String issuerUri;

    private String jwkSetUri;

    private String rolesClaim =
            "roles";

    @PostConstruct
    void validate() {

        if (
                issuerUri == null
                        ||
                        issuerUri.isBlank()
        ) {

            throw new IllegalStateException(
                    "security.jwt.issuer-uri is required"
            );
        }

        if (
                jwkSetUri == null
                        ||
                        jwkSetUri.isBlank()
        ) {

            throw new IllegalStateException(
                    "security.jwt.jwk-set-uri is required"
            );
        }

        if (
                rolesClaim == null
                        ||
                        rolesClaim.isBlank()
        ) {

            throw new IllegalStateException(
                    "security.jwt.roles-claim is required"
            );
        }

        issuerUri =
                issuerUri.trim();

        jwkSetUri =
                jwkSetUri.trim();

        rolesClaim =
                rolesClaim.trim();
    }

    public String getIssuerUri() {

        return issuerUri;
    }

    public void setIssuerUri(
            String issuerUri
    ) {

        this.issuerUri =
                issuerUri;
    }

    public String getJwkSetUri() {

        return jwkSetUri;
    }

    public void setJwkSetUri(
            String jwkSetUri
    ) {

        this.jwkSetUri =
                jwkSetUri;
    }

    public String getRolesClaim() {

        return rolesClaim;
    }

    public void setRolesClaim(
            String rolesClaim
    ) {

        this.rolesClaim =
                rolesClaim;
    }
}
