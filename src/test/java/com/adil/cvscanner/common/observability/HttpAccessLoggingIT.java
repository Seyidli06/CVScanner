package com.adil.cvscanner.common.observability;

import com.adil.cvscanner.security.SecurityTestUsers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static com.adil.cvscanner.common.observability.CorrelationIdFilter.CORRELATION_ID_HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class HttpAccessLoggingIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    "postgres:16-alpine"
            )
                    .withDatabaseName(
                            "cvscanner_access_log_test"
                    )
                    .withUsername(
                            "test"
                    )
                    .withPassword(
                            "test"
                    );

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldLogSafeHttpMetadata(
            CapturedOutput output
    ) throws Exception {

        String correlationId =
                "access-test-123";

        mockMvc.perform(
                        get(
                                "/api/v1/candidates"
                        )
                                .header(
                                        CORRELATION_ID_HEADER,
                                        correlationId
                                )
                                .with(
                                        SecurityTestUsers.recruiter()
                                )
                )
                .andExpect(
                        status()
                                .isOk()
                );

        String accessLogs =
                accessLogsOnly(
                        output.getOut()
                );

        assertThat(
                accessLogs
        )
                .contains(
                        "HTTP_ACCESS"
                )
                .contains(
                        "method=GET"
                )
                .contains(
                        "path=/api/v1/candidates"
                )
                .contains(
                        "status=200"
                )
                .contains(
                        "durationMs="
                )
                .contains(
                        "correlationId="
                                + correlationId
                );
    }

    @Test
    void shouldNotLogQueryString(
            CapturedOutput output
    ) throws Exception {

        String sensitiveValue =
                "do-not-log-this-secret";

        mockMvc.perform(
                        get(
                                "/api/v1/candidates"
                        )
                                .param(
                                        "location",
                                        sensitiveValue
                                )
                                .header(
                                        CORRELATION_ID_HEADER,
                                        "query-test-123"
                                )
                                .with(
                                        SecurityTestUsers.recruiter()
                                )
                )
                .andExpect(
                        status()
                                .isOk()
                );

        String accessLogs =
                accessLogsOnly(
                        output.getOut()
                );

        assertThat(
                accessLogs
        )
                .contains(
                        "path=/api/v1/candidates"
                )
                .doesNotContain(
                        sensitiveValue
                )
                .doesNotContain(
                        "?location="
                );
    }

    @Test
    void shouldNotLogAuthorizationOrCookie(
            CapturedOutput output
    ) throws Exception {

        String authorizationHeader =
                "Basic super-secret-authorization-value";

        String cookie =
                "SESSION=super-secret-session-value";

        mockMvc.perform(
                        get(
                                "/api/v1/candidates"
                        )
                                .header(
                                        CORRELATION_ID_HEADER,
                                        "security-test-123"
                                )
                                .header(
                                        "Authorization",
                                        authorizationHeader
                                )
                                .header(
                                        "Cookie",
                                        cookie
                                )
                                .with(
                                        SecurityTestUsers.recruiter()
                                )
                )
                .andExpect(
                        status()
                                .isOk()
                );

        String accessLogs =
                accessLogsOnly(
                        output.getOut()
                );

        assertThat(
                accessLogs
        )
                .doesNotContain(
                        authorizationHeader
                )
                .doesNotContain(
                        "super-secret-authorization-value"
                )
                .doesNotContain(
                        cookie
                )
                .doesNotContain(
                        "super-secret-session-value"
                );
    }

    @Test
    void shouldLogHttpErrorStatus(
            CapturedOutput output
    ) throws Exception {

        mockMvc.perform(
                        get(
                                "/api/v1/candidates"
                        )
                                .param(
                                        "size",
                                        "101"
                                )
                                .header(
                                        CORRELATION_ID_HEADER,
                                        "bad-request-test-123"
                                )
                                .with(
                                        SecurityTestUsers.recruiter()
                                )
                )
                .andExpect(
                        status()
                                .isBadRequest()
                );

        String accessLogs =
                accessLogsOnly(
                        output.getOut()
                );

        assertThat(
                accessLogs
        )
                .contains(
                        "method=GET"
                )
                .contains(
                        "path=/api/v1/candidates"
                )
                .contains(
                        "status=400"
                )
                .contains(
                        "correlationId=bad-request-test-123"
                );
    }

    @Test
    void shouldNotAccessLogHealthProbe(
            CapturedOutput output
    ) throws Exception {

        mockMvc.perform(
                        get(
                                "/livez"
                        )
                )
                .andExpect(
                        status()
                                .isOk()
                );

        String accessLogs =
                accessLogsOnly(
                        output.getOut()
                );

        assertThat(
                accessLogs
        ).doesNotContain(
                "path=/livez"
        );
    }

    private String accessLogsOnly(
            String output
    ) {

        return output
                .lines()
                .filter(
                        line ->
                                line.contains(
                                        "HTTP_ACCESS"
                                )
                )
                .reduce(
                        "",
                        (
                                left,
                                right
                        ) ->
                                left
                                        + System.lineSeparator()
                                        + right
                );
    }
}
