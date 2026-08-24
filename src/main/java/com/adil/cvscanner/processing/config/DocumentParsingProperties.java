package com.adil.cvscanner.processing.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.parsing")
public record DocumentParsingProperties(

        @Min(1)
        int maxTextLength

) {
}