package com.adil.cvscanner;

import com.adil.cvscanner.processing.config.DocumentParsingProperties;
import com.adil.cvscanner.upload.config.UploadStorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        UploadStorageProperties.class,
        DocumentParsingProperties.class
})
public class CvScannerApplication {

    public static void main(String[] args) {
        SpringApplication.run(
                CvScannerApplication.class,
                args
        );
    }
}
