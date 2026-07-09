package com.roadeye.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;
    private static final String TEST_SECRET = "test-secret-key-that-is-long-enough-for-hmac-sha-algorithm";

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(TEST_SECRET, 3600000L);
    }

    @Test
    void generateToken_shouldReturnValidToken() {
        String token = tokenProvider.generateToken("admin", List.of("SUPER_ADMIN"));
        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    void generateToken_shouldContainUsernameAndRoles() {
        String token = tokenProvider.generateToken("operator", List.of("TRAFFIC_OFFICER", "VIOLATION_REVIEWER"));

        String username = tokenProvider.getUsernameFromToken(token);
        assertEquals("operator", username);
    }

    @Test
    void validateToken_validToken_returnsTrue() {
        String token = tokenProvider.generateToken("admin", List.of("SUPER_ADMIN"));
        assertTrue(tokenProvider.validateToken(token));
    }

    @Test
    void validateToken_invalidToken_returnsFalse() {
        assertFalse(tokenProvider.validateToken("invalid.jwt.token"));
    }

    @Test
    void validateToken_expiredToken_returnsFalse() {
        JwtTokenProvider shortLived = new JwtTokenProvider(TEST_SECRET, -1L);
        String token = shortLived.generateToken("admin", List.of("SUPER_ADMIN"));
        assertFalse(shortLived.validateToken(token));
    }

    @Test
    void getUsernameFromToken_shouldReturnCorrectUsername() {
        String token = tokenProvider.generateToken("testuser", List.of("AUDITOR"));
        assertEquals("testuser", tokenProvider.getUsernameFromToken(token));
    }
}
