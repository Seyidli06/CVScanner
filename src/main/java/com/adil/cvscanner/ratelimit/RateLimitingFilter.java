package com.adil.cvscanner.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

public class RateLimitingFilter
        extends OncePerRequestFilter {

    private final RateLimitPolicyResolver policyResolver;

    private final RateLimitService rateLimitService;

    private final RateLimitResponseWriter responseWriter;

    public RateLimitingFilter(
            RateLimitPolicyResolver policyResolver,
            RateLimitService rateLimitService,
            RateLimitResponseWriter responseWriter
    ) {

        this.policyResolver =
                Objects.requireNonNull(
                        policyResolver,
                        "policyResolver must not be null"
                );

        this.rateLimitService =
                Objects.requireNonNull(
                        rateLimitService,
                        "rateLimitService must not be null"
                );

        this.responseWriter =
                Objects.requireNonNull(
                        responseWriter,
                        "responseWriter must not be null"
                );
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String path =
                resolveApplicationPath(
                        request
                );

        Optional<RateLimitPolicy> policy =
                policyResolver.resolve(
                        request.getMethod(),
                        path
                );

        if (
                policy.isEmpty()
        ) {

            filterChain.doFilter(
                    request,
                    response
            );

            return;
        }

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (
                authentication == null
                        ||
                        !authentication.isAuthenticated()
                        ||
                        authentication
                                instanceof AnonymousAuthenticationToken
        ) {

            filterChain.doFilter(
                    request,
                    response
            );

            return;
        }

        String principal =
                authentication.getName();

        if (
                principal == null
                        ||
                        principal.isBlank()
        ) {

            filterChain.doFilter(
                    request,
                    response
            );

            return;
        }

        RateLimitDecision decision =
                rateLimitService.consume(
                        policy.get(),
                        principal
                );

        if (
                !decision.backendAvailable()
        ) {

            if (
                    decision.allowed()
            ) {

                filterChain.doFilter(
                        request,
                        response
                );

                return;
            }

            responseWriter.writeBackendUnavailable(
                    request,
                    response
            );

            return;
        }

        if (
                !decision.allowed()
        ) {

            responseWriter.writeRateLimitExceeded(
                    request,
                    response,
                    decision
            );

            return;
        }

        response.setHeader(
                RateLimitResponseWriter
                        .RATE_LIMIT_REMAINING_HEADER,
                Long.toString(
                        decision.remainingTokens()
                )
        );

        filterChain.doFilter(
                request,
                response
        );
    }

    private String resolveApplicationPath(
            HttpServletRequest request
    ) {

        String requestUri =
                request.getRequestURI();

        String contextPath =
                request.getContextPath();

        if (
                contextPath != null
                        &&
                        !contextPath.isEmpty()
                        &&
                        requestUri.startsWith(
                                contextPath
                        )
        ) {

            return requestUri.substring(
                    contextPath.length()
            );
        }

        return requestUri;
    }
}
