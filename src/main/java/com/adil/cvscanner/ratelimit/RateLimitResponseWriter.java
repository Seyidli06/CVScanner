package com.adil.cvscanner.ratelimit;

import com.adil.cvscanner.common.api.ApiErrorCode;
import com.adil.cvscanner.common.api.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

public class RateLimitResponseWriter {

    public static final String RATE_LIMIT_REMAINING_HEADER =
            "X-Rate-Limit-Remaining";

    private static final String RATE_LIMIT_EXCEEDED_MESSAGE =
            "Too many requests. Please try again later.";

    private static final String BACKEND_UNAVAILABLE_MESSAGE =
            "Rate limiting service is temporarily unavailable.";

    private final JsonMapper jsonMapper;

    public RateLimitResponseWriter(
            JsonMapper jsonMapper
    ) {

        this.jsonMapper =
                Objects.requireNonNull(
                        jsonMapper,
                        "jsonMapper must not be null"
                );
    }

    public void writeRateLimitExceeded(
            HttpServletRequest request,
            HttpServletResponse response,
            RateLimitDecision decision
    ) throws IOException {

        Objects.requireNonNull(
                decision,
                "decision must not be null"
        );

        long retryAfterSeconds =
                toRetryAfterSeconds(
                        decision.retryAfter()
                );

        response.setHeader(
                HttpHeaders.RETRY_AFTER,
                Long.toString(
                        retryAfterSeconds
                )
        );

        response.setHeader(
                RATE_LIMIT_REMAINING_HEADER,
                Long.toString(
                        decision.remainingTokens()
                )
        );

        writeError(
                request,
                response,
                HttpStatus.TOO_MANY_REQUESTS,
                ApiErrorCode.RATE_LIMIT_EXCEEDED,
                RATE_LIMIT_EXCEEDED_MESSAGE
        );
    }

    public void writeBackendUnavailable(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        writeError(
                request,
                response,
                HttpStatus.SERVICE_UNAVAILABLE,
                ApiErrorCode.RATE_LIMIT_BACKEND_UNAVAILABLE,
                BACKEND_UNAVAILABLE_MESSAGE
        );
    }

    private void writeError(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            ApiErrorCode code,
            String message
    ) throws IOException {

        ApiErrorResponse body =
                ApiErrorResponse.of(
                        status,
                        code,
                        message,
                        request.getRequestURI()
                );

        response.setStatus(
                status.value()
        );

        response.setContentType(
                MediaType.APPLICATION_JSON_VALUE
        );

        response.setCharacterEncoding(
                StandardCharsets.UTF_8.name()
        );

        jsonMapper.writeValue(
                response.getOutputStream(),
                body
        );
    }

    private long toRetryAfterSeconds(
            Duration duration
    ) {

        long seconds =
                duration.getSeconds();

        if (
                duration.getNano() > 0
        ) {

            seconds++;
        }

        return Math.max(
                1L,
                seconds
        );
    }
}
