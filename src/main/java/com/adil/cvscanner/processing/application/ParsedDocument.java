package com.adil.cvscanner.processing.application;

import java.nio.file.Path;

public record ParsedDocument(

        Path source,
        String mediaType,
        String text

) {
}
