package com.adil.cvscanner.common.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class HttpAccessLoggingFilter
        extends OncePerRequestFilter {

    private static final Logger log =
            LoggerFactory.getLogger(
                    HttpAccessLoggingFilter.class
            );

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        long startedAtNanos =
                System.nanoTime();

        try {

            filterChain.doFilter(
                    request,
                    response
            );

        } finally {

            long durationMs =
                    TimeUnit.NANOSECONDS.toMillis(
                            System.nanoTime()
                                    - startedAtNanos
                    );

            String correlationId =
                    MDC.get(
                            CorrelationIdFilter
                                    .MDC_CORRELATION_ID_KEY
                    );

            if (
                    correlationId == null
                            || correlationId.isBlank()
            ) {

                correlationId = "-";
            }

            













            log.info(
                    "HTTP_ACCESS method={} path={} status={} durationMs={} correlationId={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    durationMs,
                    correlationId
            );
        }
    }

    










    @Override
    protected boolean shouldNotFilter(
            HttpServletRequest request
    ) {

        String path =
                request.getRequestURI();

        return path.equals(
                "/livez"
        )
                ||
                path.equals(
                        "/readyz"
                )
                ||
                path.equals(
                        "/actuator/health"
                )
                ||
                path.startsWith(
                        "/actuator/health/"
                );
    }
}