package com.adil.cvscanner.config;

import com.adil.cvscanner.CvScannerApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionProfileFailFastIT {

    @Test
    void shouldFailToStartWhenProductionInfrastructureConfigurationIsMissing() {

        SpringApplication application =
                new SpringApplication(
                        CvScannerApplication.class
                );

        application.setWebApplicationType(
                WebApplicationType.NONE
        );

        ConfigurableEnvironment environment =
                new StandardEnvironment();

        environment
                .getPropertySources()
                .remove(
                        StandardEnvironment
                                .SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME
                );

        environment
                .getPropertySources()
                .remove(
                        StandardEnvironment
                                .SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME
                );

        environment.setActiveProfiles(
                "prod"
        );

        application.setEnvironment(
                environment
        );

        assertThatThrownBy(
                application::run
        )
                .isInstanceOf(
                        RuntimeException.class
                );
    }
}