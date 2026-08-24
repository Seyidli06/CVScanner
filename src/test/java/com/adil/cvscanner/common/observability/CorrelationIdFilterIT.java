package com.adil.cvscanner.common.observability;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static com.adil.cvscanner.common.observability.CorrelationIdFilter.CORRELATION_ID_HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CorrelationIdFilterIT {

    /*
     * ============================================================
     * REAL POSTGRESQL
     * ============================================================
     *
     * Full Spring application context başladırıq.
     *
     * localhost:5435 dependency-si yoxdur.
     */
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    "postgres:16-alpine"
            )
                    .withDatabaseName(
                            "cvscanner_correlation_test"
                    )
                    .withUsername(
                            "test"
                    )
                    .withPassword(
                            "test"
                    );

    @Autowired
    private MockMvc mockMvc;

    /*
     * ============================================================
     * TEST 1
     * CLIENT PROVIDES VALID CORRELATION ID
     * ============================================================
     *
     * Request:
     *
     * X-Correlation-ID: frontend-request-123
     *
     * Response:
     *
     * X-Correlation-ID: frontend-request-123
     */

    @Test
    void shouldPreserveValidIncomingCorrelationId()
            throws Exception {

        String correlationId =
                "frontend-request-123";

        mockMvc.perform(
                        get(
                                "/livez"
                        )
                                .header(
                                        CORRELATION_ID_HEADER,
                                        correlationId
                                )
                )
                .andExpect(
                        status()
                                .isOk()
                )
                .andExpect(
                        header()
                                .string(
                                        CORRELATION_ID_HEADER,
                                        correlationId
                                )
                );
    }

    /*
     * ============================================================
     * TEST 2
     * HEADER MISSING
     * ============================================================
     *
     * Client correlation ID verməyib.
     *
     * Server UUID yaratmalıdır.
     */

    @Test
    void shouldGenerateCorrelationIdWhenHeaderIsMissing()
            throws Exception {

        String generatedCorrelationId =
                mockMvc.perform(
                                get(
                                        "/livez"
                                )
                        )
                        .andExpect(
                                status()
                                        .isOk()
                        )
                        .andExpect(
                                header()
                                        .exists(
                                                CORRELATION_ID_HEADER
                                        )
                        )
                        .andReturn()
                        .getResponse()
                        .getHeader(
                                CORRELATION_ID_HEADER
                        );

        assertThat(
                generatedCorrelationId
        ).isNotBlank();

        /*
         * Bizim generated format UUID-dir.
         */
        UUID parsed =
                UUID.fromString(
                        generatedCorrelationId
                );

        assertThat(
                parsed.toString()
        ).isEqualTo(
                generatedCorrelationId
        );
    }

    /*
     * ============================================================
     * TEST 3
     * INVALID CLIENT VALUE
     * ============================================================
     *
     * Space character allowlist-də yoxdur.
     *
     * Buna görə incoming value reject olunur
     * və yeni UUID yaradılır.
     */

    @Test
    void shouldReplaceInvalidIncomingCorrelationId()
            throws Exception {

        String invalidCorrelationId =
                "bad correlation id";

        String responseCorrelationId =
                mockMvc.perform(
                                get(
                                        "/livez"
                                )
                                        .header(
                                                CORRELATION_ID_HEADER,
                                                invalidCorrelationId
                                        )
                        )
                        .andExpect(
                                status()
                                        .isOk()
                        )
                        .andExpect(
                                header()
                                        .exists(
                                                CORRELATION_ID_HEADER
                                        )
                        )
                        .andReturn()
                        .getResponse()
                        .getHeader(
                                CORRELATION_ID_HEADER
                        );

        assertThat(
                responseCorrelationId
        ).isNotEqualTo(
                invalidCorrelationId
        );

        UUID.fromString(
                responseCorrelationId
        );
    }

    /*
     * ============================================================
     * TEST 4
     * TOO LONG VALUE
     * ============================================================
     *
     * Max:
     *
     * 128
     *
     * 129-character client input reject olunur.
     */

    @Test
    void shouldReplaceTooLongCorrelationId()
            throws Exception {

        String tooLongCorrelationId =
                "a".repeat(
                        129
                );

        String responseCorrelationId =
                mockMvc.perform(
                                get(
                                        "/livez"
                                )
                                        .header(
                                                CORRELATION_ID_HEADER,
                                                tooLongCorrelationId
                                        )
                        )
                        .andExpect(
                                status()
                                        .isOk()
                        )
                        .andReturn()
                        .getResponse()
                        .getHeader(
                                CORRELATION_ID_HEADER
                        );

        assertThat(
                responseCorrelationId
        )
                .isNotBlank()
                .isNotEqualTo(
                        tooLongCorrelationId
                );

        UUID.fromString(
                responseCorrelationId
        );
    }

    /*
     * ============================================================
     * TEST 5
     * EVERY REQUEST GETS ITS OWN ID
     * ============================================================
     *
     * MDC/thread reuse səbəbindən əvvəlki request
     * ID-si növbəti request-ə keçməməlidir.
     */

    @Test
    void shouldGenerateIndependentCorrelationIdsForSeparateRequests()
            throws Exception {

        String firstCorrelationId =
                mockMvc.perform(
                                get(
                                        "/livez"
                                )
                        )
                        .andExpect(
                                status()
                                        .isOk()
                        )
                        .andReturn()
                        .getResponse()
                        .getHeader(
                                CORRELATION_ID_HEADER
                        );

        String secondCorrelationId =
                mockMvc.perform(
                                get(
                                        "/livez"
                                )
                        )
                        .andExpect(
                                status()
                                        .isOk()
                        )
                        .andReturn()
                        .getResponse()
                        .getHeader(
                                CORRELATION_ID_HEADER
                        );

        assertThat(
                firstCorrelationId
        ).isNotBlank();

        assertThat(
                secondCorrelationId
        ).isNotBlank();

        assertThat(
                firstCorrelationId
        ).isNotEqualTo(
                secondCorrelationId
        );
    }
}