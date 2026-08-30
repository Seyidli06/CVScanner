package com.adil.cvscanner.processing.application;

public class DocumentParsingException
        extends RuntimeException {

    private final DocumentErrorCode code;

    public DocumentParsingException(
            DocumentErrorCode code,
            String message
    ) {
        super(message);
        this.code = code;
    }

    public DocumentParsingException(
            DocumentErrorCode code,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.code = code;
    }

    public DocumentErrorCode getCode() {
        return code;
    }
}
