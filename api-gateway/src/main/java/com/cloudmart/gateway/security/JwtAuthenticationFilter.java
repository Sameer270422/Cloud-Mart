package com.cloudmart.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Every request routed by the gateway passes through here first. Unless it
 * matches the public allowlist below, it needs a valid, non-expired bearer
 * token or the gateway rejects it with 401 before it ever reaches a
 * downstream service - previously every service except user-service had no
 * auth at all, so this is the boundary that actually enforces it.
 *
 * On success, the caller's identity is injected as trusted headers
 * (X-User-Id / X-User-Email / X-User-Role) that downstream services can rely
 * on without re-verifying the token themselves. Any client-supplied copies
 * of those headers are stripped first, on every request (public or not) -
 * otherwise a request could simply set X-User-Id itself and impersonate
 * anyone, exactly the gap user-service's now-legitimate /api/users/me
 * endpoint used to be exposed to.
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final List<PublicRoute> PUBLIC_ROUTES = List.of(
            new PublicRoute(HttpMethod.POST, "/api/auth/**"),
            new PublicRoute(HttpMethod.GET, "/api/products/**"),
            new PublicRoute(HttpMethod.GET, "/api/assistant/search"));

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final JwtValidator jwtValidator;

    public JwtAuthenticationFilter(JwtValidator jwtValidator) {
        this.jwtValidator = jwtValidator;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        if (isPublicRoute(request)) {
            return chain.filter(exchange.mutate().request(stripTrustedHeaders(request)).build());
        }

        String token = extractBearerToken(request);
        if (token == null) {
            return reject(exchange, "Missing bearer token");
        }

        Claims claims;
        try {
            claims = jwtValidator.parse(token);
        } catch (JwtException | IllegalArgumentException ex) {
            return reject(exchange, "Invalid or expired token");
        }

        ServerHttpRequest.Builder mutated = stripTrustedHeaders(request).mutate();
        mutated.header(TrustedHeaders.USER_ID, String.valueOf(claims.get("userId", Number.class).longValue()));
        mutated.header(TrustedHeaders.USER_EMAIL, claims.getSubject());
        mutated.header(TrustedHeaders.USER_ROLE, claims.get("role", String.class));

        return chain.filter(exchange.mutate().request(mutated.build()).build());
    }

    private ServerHttpRequest stripTrustedHeaders(ServerHttpRequest request) {
        return request.mutate()
                .headers(headers -> {
                    headers.remove(TrustedHeaders.USER_ID);
                    headers.remove(TrustedHeaders.USER_EMAIL);
                    headers.remove(TrustedHeaders.USER_ROLE);
                })
                .build();
    }

    private boolean isPublicRoute(ServerHttpRequest request) {
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();
        return PUBLIC_ROUTES.stream().anyMatch(route ->
                route.method().equals(method) && PATH_MATCHER.match(route.pattern(), path));
    }

    private String extractBearerToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private Mono<Void> reject(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = ("{\"error\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }

    // Runs before Spring Cloud Gateway's routing filters so an unauthorized
    // request never reaches a downstream service.
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private record PublicRoute(HttpMethod method, String pattern) {}
}
