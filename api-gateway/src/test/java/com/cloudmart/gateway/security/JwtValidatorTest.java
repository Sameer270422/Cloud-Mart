package com.cloudmart.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtValidatorTest {

    private static final String SECRET = "test-secret-key-at-least-32-bytes-long-for-hs256";

    private JwtValidator validator;

    @BeforeEach
    void setUp() {
        validator = new JwtValidator();
        ReflectionTestUtils.setField(validator, "secret", SECRET);
    }

    private String token(String secret, long userId, String role, long expiresInMs) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes());
        Date now = new Date();
        return Jwts.builder()
                .subject("jane@example.com")
                .claims(Map.of("userId", userId, "role", role))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiresInMs))
                .signWith(key)
                .compact();
    }

    @Test
    void parsesAValidTokenSignedWithTheSameSecret() {
        String token = token(SECRET, 42L, "CUSTOMER", 60_000);

        Claims claims = validator.parse(token);

        assertThat(claims.getSubject()).isEqualTo("jane@example.com");
        assertThat(claims.get("userId", Number.class).longValue()).isEqualTo(42L);
        assertThat(claims.get("role", String.class)).isEqualTo("CUSTOMER");
    }

    @Test
    void rejectsATokenSignedWithADifferentSecret() {
        String token = token("a-completely-different-secret-key-32-bytes!", 42L, "CUSTOMER", 60_000);

        assertThatThrownBy(() -> validator.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsAnExpiredToken() {
        String token = token(SECRET, 42L, "CUSTOMER", -1_000);

        assertThatThrownBy(() -> validator.parse(token)).isInstanceOf(JwtException.class);
    }
}
