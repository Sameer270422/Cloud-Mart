package com.cloudmart.gateway.security;

/**
 * Headers the gateway injects for a successfully-verified request. Downstream
 * services trust these unconditionally - they are only ever set here, after
 * signature/expiry verification, and any client-supplied copies are stripped
 * first (see JwtAuthenticationFilter) so a request can't forge them by simply
 * setting the header itself.
 */
public final class TrustedHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_EMAIL = "X-User-Email";
    public static final String USER_ROLE = "X-User-Role";

    private TrustedHeaders() {}
}
