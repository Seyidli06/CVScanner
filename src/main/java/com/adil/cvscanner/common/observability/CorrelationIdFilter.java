package com.adil.cvscanner.common.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER =
            "X-Correlation-ID";

    public static final String MDC_CORRELATION_ID_KEY =
            "correlationId";

    private static final Pattern VALID_CORRELATION_ID =
            Pattern.compile(
                    "^[A-Za-z0-9._:-]{1,128}$"
            );

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String correlationId =
                resolveCorrelationId(
                        request.getHeader(
                                CORRELATION_ID_HEADER
                        )
                );

        response.setHeader(
                CORRELATION_ID_HEADER,
                correlationId
        );

        MDC.put(
                MDC_CORRELATION_ID_KEY,
                correlationId
        );

        try {

            filterChain.doFilter(
                    request,
                    response
            );

        } finally {

            MDC.remove(
                    MDC_CORRELATION_ID_KEY
            );
        }
    }

    private String resolveCorrelationId(
            String incomingCorrelationId
    ) {

        if (
                incomingCorrelationId != null
                        &&
                        VALID_CORRELATION_ID
                                .matcher(
                                        incomingCorrelationId
                                )
                                .matches()
        ) {

            return incomingCorrelationId;
        }

        return UUID
                .randomUUID()
                .toString();
    }
}
