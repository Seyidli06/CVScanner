package com.adil.cvscanner.processing.infrastructure;

import com.adil.cvscanner.processing.application.DocumentErrorCode;
import com.adil.cvscanner.processing.application.DocumentParsingException;
import com.adil.cvscanner.processing.application.DocumentTextExtractor;
import com.adil.cvscanner.processing.application.ParsedDocument;
import com.adil.cvscanner.processing.config.DocumentParsingProperties;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

@Component
public class TikaDocumentTextExtractor implements DocumentTextExtractor {

    private static final String PDF_MEDIA_TYPE =
            "application/pdf";

    private static final String DOCX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private static final Set<String> ALLOWED_MEDIA_TYPES =
            Set.of(
                    PDF_MEDIA_TYPE,
                    DOCX_MEDIA_TYPE
            );

    private final Tika tika;
    private final AutoDetectParser parser;
    private final int maxTextLength;

    public TikaDocumentTextExtractor(
            DocumentParsingProperties properties
    ) {
        this.tika = new Tika();
        this.parser = new AutoDetectParser();
        this.maxTextLength = properties.maxTextLength();
    }

    @Override
    public ParsedDocument extract(Path document) {

        validateFile(document);

        String mediaType =
                detectMediaType(document);

        validateMediaType(
                document,
                mediaType
        );

        String text =
                extractText(document);

        if (text.isBlank()) {
            throw new DocumentParsingException(
                    DocumentErrorCode.EMPTY_DOCUMENT,
                    "Document does not contain extractable text: "
                            + document.getFileName()
            );
        }

        return new ParsedDocument(
                document,
                mediaType,
                text
        );
    }

    private void validateFile(Path document) {

        if (document == null) {
            throw new DocumentParsingException(
                    DocumentErrorCode.FILE_NOT_FOUND,
                    "Document path must not be null"
            );
        }

        if (
                Files.notExists(document)
                        || !Files.isRegularFile(document)
        ) {
            throw new DocumentParsingException(
                    DocumentErrorCode.FILE_NOT_FOUND,
                    "Document does not exist: "
                            + document.getFileName()
            );
        }
    }

    private String detectMediaType(Path document) {

        try {
            return tika.detect(document);

        } catch (IOException exception) {

            throw new DocumentParsingException(
                    DocumentErrorCode.PARSE_FAILED,
                    "Failed to detect document type: "
                            + document.getFileName(),
                    exception
            );
        }
    }

    private void validateMediaType(
            Path document,
            String mediaType
    ) {

        if (!ALLOWED_MEDIA_TYPES.contains(mediaType)) {

            throw new DocumentParsingException(
                    DocumentErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "Unsupported actual document type '"
                            + mediaType
                            + "' for file: "
                            + document.getFileName()
            );
        }
    }

    private String extractText(Path document) {

        Metadata metadata =
                new Metadata();

        BodyContentHandler handler =
                new BodyContentHandler(
                        maxTextLength
                );

        ParseContext context =
                new ParseContext();

        try (
                InputStream inputStream =
                        Files.newInputStream(document)
        ) {

            parser.parse(
                    inputStream,
                    handler,
                    metadata,
                    context
            );

            return normalizeText(
                    handler.toString()
            );

        } catch (SAXException exception) {

            if (
                    WriteLimitReachedException
                            .isWriteLimitReached(exception)
            ) {

                throw new DocumentParsingException(
                        DocumentErrorCode.TEXT_LIMIT_EXCEEDED,
                        "Extracted text exceeds maximum allowed length: "
                                + document.getFileName(),
                        exception
                );
            }

            throw new DocumentParsingException(
                    DocumentErrorCode.PARSE_FAILED,
                    "Failed to parse document: "
                            + document.getFileName(),
                    exception
            );

        } catch (
                IOException
                | TikaException exception
        ) {

            throw new DocumentParsingException(
                    DocumentErrorCode.PARSE_FAILED,
                    "Failed to parse document: "
                            + document.getFileName(),
                    exception
            );
        }
    }

    private String normalizeText(String text) {

        return text
                .replace("\u0000", "")
                .replaceAll(
                        "[\\t\\x0B\\f\\r ]+",
                        " "
                )
                .replaceAll(
                        "\\n{3,}",
                        "\n\n"
                )
                .trim();
    }
}
