package com.adil.cvscanner.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(
        proxyBeanMethods = false
)
public class OpenApiConfiguration {

    private static final String SECURITY_SCHEME_NAME =
            "bearerAuth";

    @Bean
    OpenAPI cvScannerOpenApi() {

        return new OpenAPI()
                .info(
                        new Info()
                                .title(
                                        "CVScanner API"
                                )
                                .description(
                                        """
                                        Automated bulk CV parsing and candidate extraction API.

                                        Main capabilities:

                                        - ZIP CV upload
                                        - asynchronous Spring Batch processing
                                        - PDF/DOCX text extraction
                                        - candidate search and filtering
                                        - processing failure tracking
                                        - CSV export
                                        - XLSX export
                                        - JWT authentication
                                        - Redis-backed distributed rate limiting
                                        """
                                )
                                .version(
                                        "1.0.0"
                                )
                                .contact(
                                        new Contact()
                                                .name(
                                                        "CVScanner"
                                                )
                                )
                )
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        SECURITY_SCHEME_NAME,
                                        new SecurityScheme()
                                                .name(
                                                        SECURITY_SCHEME_NAME
                                                )
                                                .type(
                                                        SecurityScheme.Type.HTTP
                                                )
                                                .scheme(
                                                        "bearer"
                                                )
                                                .bearerFormat(
                                                        "JWT"
                                                )
                                )
                );
    }
}
