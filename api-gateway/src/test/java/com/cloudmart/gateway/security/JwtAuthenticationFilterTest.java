package com.cloudmart.gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-secret-key-at-least-32-bytes-long-for-hs256";

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        JwtValidator validator = new JwtValidator();
        ReflectionTestUtils.setField(validator, "secret", SECRET);
        filter = new JwtAuthenticationFilter(validator);
    }

    private String validToken(long userId, String role) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes());
        Date now = new Date();
        return Jwts.builder()
                .subject("jane@example.com")
                .claims(Map.of("userId", userId, "role", role))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 60_000))
                .signWith(key)
                .compact();
    }

    // Captures the request the chain actually received, so we can assert on
    // the headers the filter injected (or stripped).
    private static class CapturingChain implements GatewayFilterChain {
        final AtomicReference<ServerHttpRequest> received = new AtomicReference<>();

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            received.set(exchange.getRequest());
            return Mono.empty();
        }
    }

    @Test
    void allowsPublicGetProductsWithoutAToken() {
        var request = MockServerHttpRequest.get("/api/products").build();
        var exchange = MockServerWebExchange.from(request);
        var chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.received.get()).isNotNull();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void stripsClientSuppliedTrustedHeadersOnPublicRoutes() {
        var request = MockServerHttpRequest.get("/api/products")
                .header(TrustedHeaders.USER_ID, "999")
                .header(TrustedHeaders.USER_ROLE, "ADMIN")
                .build();
        var exchange = MockServerWebExchange.from(request);
        var chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.received.get().getHeaders().getFirst(TrustedHeaders.USER_ID)).isNull();
        assertThat(chain.received.get().getHeaders().getFirst(TrustedHeaders.USER_ROLE)).isNull();
    }

    @Test
    void rejectsAProtectedRouteWithNoToken() {
        var request = MockServerHttpRequest.get("/api/orders").build();
        var exchange = MockServerWebExchange.from(request);
        var chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.received.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsAProtectedRouteWithAnInvalidToken() {
        var request = MockServerHttpRequest.get("/api/orders")
                .header("Authorization", "Bearer not-a-real-token")
                .build();
        var exchange = MockServerWebExchange.from(request);
        var chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.received.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void injectsTrustedHeadersFromAValidTokenAndIgnoresAnySpoofedOnes() {
        String token = validToken(42L, "CUSTOMER");
        var request = MockServerHttpRequest.get("/api/orders")
                .header("Authorization", "Bearer " + token)
                .header(TrustedHeaders.USER_ID, "999")   // attempted spoof
                .header(TrustedHeaders.USER_ROLE, "ADMIN") // attempted spoof
                .build();
        var exchange = MockServerWebExchange.from(request);
        var chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        ServerHttpRequest forwarded = chain.received.get();
        assertThat(forwarded).isNotNull();
        assertThat(forwarded.getHeaders().getFirst(TrustedHeaders.USER_ID)).isEqualTo("42");
        assertThat(forwarded.getHeaders().getFirst(TrustedHeaders.USER_EMAIL)).isEqualTo("jane@example.com");
        assertThat(forwarded.getHeaders().getFirst(TrustedHeaders.USER_ROLE)).isEqualTo("CUSTOMER");
    }
}
