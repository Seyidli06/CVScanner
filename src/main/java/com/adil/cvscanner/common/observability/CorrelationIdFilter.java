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

    /*
     * ============================================================
     * PUBLIC CONTRACT
     * ============================================================
     *
     * Client bu header-i göndərə bilər:
     *
     * X-Correlation-ID: checkout-123
     *
     * Eyni value response-da geri qaytarılır.
     */
    public static final String CORRELATION_ID_HEADER =
            "X-Correlation-ID";

    /*
     * ============================================================
     * MDC KEY
     * ============================================================
     *
     * Log pattern daxilində:
     *
     * %X{correlationId}
     *
     * ilə istifadə edə biləcəyik.
     */
    public static final String MDC_CORRELATION_ID_KEY =
            "correlationId";

    /*
     * ============================================================
     * CORRELATION ID VALIDATION
     * ============================================================
     *
     * Qəbul edirik:
     *
     * letters
     * digits
     * -
     * _
     * .
     * :
     *
     * maksimum 128 character.
     *
     * Bunun məqsədi client-in arbitrary text,
     * newline və s. log context-ə daxil etməsinin
     * qarşısını almaqdır.
     */
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

        /*
         * ========================================================
         * RESPONSE PROPAGATION
         * ========================================================
         *
         * Client öz request-inin server-side correlation
         * ID-sini həmişə görə bilir.
         */
        response.setHeader(
                CORRELATION_ID_HEADER,
                correlationId
        );

        /*
         * ========================================================
         * MDC
         * ========================================================
         *
         * Bu request thread-də yazılan logların
         * hamısı həmin correlation ID-ni görə bilər.
         */
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

            /*
             * ====================================================
             * ÇOX VACİB
             * ====================================================
             *
             * Servlet thread pool thread-ləri reuse olunur.
             *
             * MDC təmizlənməsə:
             *
             * Request A
             * correlationId = A
             *
             * həmin thread sonra
             *
             * Request B
             *
             * üçün istifadə ediləndə A-nın ID-si
             * B-yə leak edə bilər.
             */
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