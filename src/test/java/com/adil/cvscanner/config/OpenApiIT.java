package com.adil.cvscanner.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("cvscanner")
                    .withUsername("cvscanner")
                    .withPassword("cvscanner");

    @DynamicPropertySource
    static void configureProperties(
            DynamicPropertyRegistry registry
    ) {

        registry.add(
                "spring.datasource.url",
                POSTGRES::getJdbcUrl
        );

        registry.add(
                "spring.datasource.username",
                POSTGRES::getUsername
        );

        registry.add(
                "spring.datasource.password",
                POSTGRES::getPassword
        );
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldExposeOpenApiSpecification()
            throws Exception {

        mockMvc.perform(
                        get("/v3/api-docs")
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.info.title")
                                .value("CVScanner API")
                )
                .andExpect(
                        jsonPath("$.info.version")
                                .value("1.0.0")
                )
                .andExpect(
                        jsonPath(
                                "$.components.securitySchemes.bearerAuth.type"
                        )
                                .value("http")
                )
                .andExpect(
                        jsonPath(
                                "$.components.securitySchemes.bearerAuth.scheme"
                        )
                                .value("bearer")
                )
                .andExpect(
                        jsonPath(
                                "$['paths']['/api/v1/candidates']"
                        )
                                .exists()
                );
    }

    @Test
    void shouldDocumentBearerAuthenticationForProtectedBusinessEndpoints()
            throws Exception {

        mockMvc.perform(
                        get("/v3/api-docs")
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath(
                                "$['paths']['/api/v1/candidates']['get']['security'][0]['bearerAuth']"
                        )
                                .isArray()
                )
                .andExpect(
                        jsonPath(
                                "$['paths']['/api/v1/candidates/export.csv']['get']['security'][0]['bearerAuth']"
                        )
                                .isArray()
                )
                .andExpect(
                        jsonPath(
                                "$['paths']['/api/v1/candidates/export.xlsx']['get']['security'][0]['bearerAuth']"
                        )
                                .isArray()
                )
                .andExpect(
                        jsonPath(
                                "$['paths']['/api/v1/uploads']['post']['security'][0]['bearerAuth']"
                        )
                                .isArray()
                )
                .andExpect(
                        jsonPath(
                                "$['paths']['/api/v1/uploads/{uploadId}']['get']['security'][0]['bearerAuth']"
                        )
                                .isArray()
                )
                .andExpect(
                        jsonPath(
                                "$['paths']['/api/v1/uploads/{uploadId}/failures']['get']['security'][0]['bearerAuth']"
                        )
                                .isArray()
                );
    }

    @Test
    void shouldExposeSwaggerUi()
            throws Exception {

        mockMvc.perform(
                        get("/swagger-ui.html")
                )
                .andExpect(
                        status().is3xxRedirection()
                );
    }
}