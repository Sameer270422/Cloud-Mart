package com.cloudmart.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

/**
 * Verifies tokens issued by user-service's JwtUtil. Deliberately independent
 * (not a shared library) - the two are small and evolving them in lockstep
 * via a shared secret is simpler than the versioning overhead of extracting
 * one.
 */
@Component
public class JwtValidator {

    @Value("${jwt.secret}")
    private String secret;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    /**
     * @throws io.jsonwebtoken.JwtException if the token is malformed, has an
     *         invalid signature, or has expired.
     */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
    }
}
