package com.adil.cvscanner.processing.application;

import java.nio.file.Path;

public interface DocumentTextExtractor {

    ParsedDocument extract(Path document);
}