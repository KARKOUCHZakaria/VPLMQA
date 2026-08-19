package com.vplmqa.gateway.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Gateway filter that validates JWTs and injects user headers.
 */
@Component
public class JwtAuthFilter extends AbstractGatewayFilterFactory<JwtAuthFilter.Config> {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final List<String> BYPASS_PREFIXES = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/actuator",
            "/v3/api-docs",
            "/swagger-ui",
            "/webjars",
            "/swagger-resources"
    );

    private final JwtService jwtService;
    private final TokenBlacklistService blacklistService;

    /**
     * Creates a new JWT auth filter.
     *
     * @param jwtService JWT validator
     * @param blacklistService blacklist service
     */
    public JwtAuthFilter(JwtService jwtService, TokenBlacklistService blacklistService) {
        super(Config.class);
        this.jwtService = jwtService;
        this.blacklistService = blacklistService;
    }

    /**
     * Applies the filter to validate tokens and add headers.
     *
     * @param config filter config
     * @return the gateway filter
     */
    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String path = exchange.getRequest().getPath().value();
            if (isBypassed(path)) {
                return chain.filter(exchange);
            }
            String token = resolveToken(exchange.getRequest().getHeaders());
            if (token == null) {
                return unauthorized("Missing Authorization header");
            }
            if (!jwtService.validateToken(token)) {
                logger.error("Token validation failed for token: {}", token);
                return unauthorized("Invalid token");
            }
            return blacklistService.isBlacklisted(token)
                    .flatMap(blacklisted -> {
                        if (blacklisted) {
                            return unauthorized("Token is blacklisted");
                        }
                        return applyHeaders(exchange, chain, token);
                     });
        };
    }

    private Mono<Void> applyHeaders(org.springframework.web.server.ServerWebExchange exchange,
                                    org.springframework.cloud.gateway.filter.GatewayFilterChain chain,
                                    String token) {
        String userId = jwtService.extractUserId(token);
        String role = jwtService.extractUserRole(token);
        logger.debug("Authenticated user {} with role {}", userId, role);
        return chain.filter(exchange.mutate()
                .request(exchange.getRequest().mutate()
                        .header("X-User-Id", userId)
                        .header("X-User-Role", role)
                        .build())
                .build());
    }

    private boolean isBypassed(String path) {
        return BYPASS_PREFIXES.stream().anyMatch(path::startsWith)
                || path.contains("/v3/api-docs")
                || path.endsWith("/api-docs");
    }

    private String resolveToken(HttpHeaders headers) {
        String authHeader = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return authHeader.substring("Bearer ".length());
    }

    private Mono<Void> unauthorized(String message) {
        return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, message));
    }

    /**
     * Empty configuration for the filter factory.
     */
    public static class Config {
        /**
         * Default constructor.
         */
        public Config() {
        }
    }
}
