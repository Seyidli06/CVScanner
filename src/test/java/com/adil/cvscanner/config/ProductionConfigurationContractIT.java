package com.adil.cvscanner.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionConfigurationContractIT {

    private static final String APPLICATION_YAML =
            "application.yaml";

    private static final String APPLICATION_PROD_YML =
            "application-prod.yml";

    @Test
    void shouldKeepProductionInfrastructureExternalized()
            throws IOException {

        String yaml =
                readClasspathResource(
                        APPLICATION_PROD_YML
                );

        assertThat(yaml)
                .contains(
                        "${CVSCANNER_DB_URL}"
                )
                .contains(
                        "${CVSCANNER_DB_USERNAME}"
                )
                .contains(
                        "${CVSCANNER_DB_PASSWORD}"
                )
                .contains(
                        "${CVSCANNER_JWT_ISSUER_URI}"
                )
                .contains(
                        "${CVSCANNER_JWT_JWK_SET_URI}"
                )
                .contains(
                        "${CVSCANNER_RATE_LIMIT_REDIS_URI}"
                )
                .contains(
                        "${CVSCANNER_STORAGE_ROOT}"
                );

        assertThat(yaml)
                .doesNotContain(
                        "jdbc:postgresql://localhost:5435/cvscanner"
                )
                .doesNotContain(
                        "redis://localhost:6385"
                )
                .doesNotContain(
                        "localhost:8180"
                )
                .doesNotContain(
                        "username: cvscanner"
                )
                .doesNotContain(
                        "password: cvscanner"
                );

        assertThat(yaml)
                .contains(
                        "include-message: never"
                )
                .contains(
                        "include-stacktrace: never"
                )
                .contains(
                        "include-binding-errors: never"
                );
    }

    @Test
    void shouldExposeExpectedProductionRuntimeDefaults()
            throws IOException {

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

        YamlPropertySourceLoader loader =
                new YamlPropertySourceLoader();

        ClassPathResource commonResource =
                new ClassPathResource(
                        APPLICATION_YAML
                );

        assertThat(
                commonResource.exists()
        )
                .as(
                        "%s must exist on the classpath",
                        APPLICATION_YAML
                )
                .isTrue();

        List<PropertySource<?>> commonSources =
                loader.load(
                        "application",
                        commonResource
                );

        for (
                PropertySource<?> propertySource
                : commonSources
        ) {

            environment
                    .getPropertySources()
                    .addLast(
                            propertySource
                    );
        }

        ClassPathResource productionResource =
                new ClassPathResource(
                        APPLICATION_PROD_YML
                );

        assertThat(
                productionResource.exists()
        )
                .as(
                        "%s must exist on the classpath",
                        APPLICATION_PROD_YML
                )
                .isTrue();

        List<PropertySource<?>> productionSources =
                loader.load(
                        "application-prod",
                        productionResource
                );

        for (
                PropertySource<?> propertySource
                : productionSources
        ) {

            environment
                    .getPropertySources()
                    .addFirst(
                            propertySource
                    );
        }

        assertThat(
                environment.getProperty(
                        "spring.datasource.hikari.maximum-pool-size",
                        Integer.class
                )
        )
                .isEqualTo(
                        10
                );

        assertThat(
                environment.getProperty(
                        "spring.datasource.hikari.minimum-idle",
                        Integer.class
                )
        )
                .isEqualTo(
                        2
                );

        assertThat(
                environment.getProperty(
                        "spring.datasource.hikari.connection-timeout",
                        Integer.class
                )
        )
                .isEqualTo(
                        5000
                );

        assertThat(
                environment.getProperty(
                        "spring.datasource.hikari.validation-timeout",
                        Integer.class
                )
        )
                .isEqualTo(
                        3000
                );

        assertThat(
                environment.getProperty(
                        "server.port",
                        Integer.class
                )
        )
                .isEqualTo(
                        8080
                );

        assertThat(
                environment.getProperty(
                        "server.shutdown"
                )
        )
                .isEqualTo(
                        "graceful"
                );

        assertThat(
                environment.getProperty(
                        "spring.lifecycle.timeout-per-shutdown-phase"
                )
        )
                .isEqualTo(
                        "30s"
                );
    }

    private String readClasspathResource(
            String resourceName
    ) throws IOException {

        ClassPathResource resource =
                new ClassPathResource(
                        resourceName
                );

        assertThat(
                resource.exists()
        )
                .as(
                        "%s must exist on the classpath",
                        resourceName
                )
                .isTrue();

        try (
                InputStream inputStream =
                        resource.getInputStream()
        ) {

            return new String(
                    inputStream.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }
    }
}
