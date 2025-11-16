package com.borsibaar.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class JwtServiceTest {

    @Autowired
    private JwtService jwtService;

    private final String testSecret = "test-secret-key-for-jwt-testing-purposes-at-least-256-bits";

    @BeforeEach
    void setUp() {
        // Set test secret key using reflection to avoid environment variable dependency
        ReflectionTestUtils.setField(jwtService, "secretKey", testSecret);
    }

    @Test
    void testGenerateToken_Success() {
        // Arrange
        String email = "test@example.com";

        // Act
        String token = jwtService.generateToken(email);

        // Assert
        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertTrue(token.split("\\.").length == 3); // JWT has 3 parts: header.payload.signature
    }

    @Test
    void testParseToken_ValidToken_ReturnsClaimsWithCorrectSubject() {
        // Arrange
        String email = "test@example.com";
        String token = jwtService.generateToken(email);

        // Act
        Claims claims = jwtService.parseToken(token);

        // Assert
        assertNotNull(claims);
        assertEquals(email, claims.getSubject());
    }

    @Test
    void testParseToken_ValidToken_HasIssuedAtDate() {
        // Arrange
        String email = "test@example.com";
        String token = jwtService.generateToken(email);

        // Act
        Claims claims = jwtService.parseToken(token);

        // Assert
        assertNotNull(claims.getIssuedAt());
        assertTrue(claims.getIssuedAt().before(new Date()) ||
                claims.getIssuedAt().equals(new Date()));
    }

    @Test
    void testParseToken_ValidToken_HasExpirationDate() {
        // Arrange
        String email = "test@example.com";
        String token = jwtService.generateToken(email);

        // Act
        Claims claims = jwtService.parseToken(token);

        // Assert
        assertNotNull(claims.getExpiration());
        assertTrue(claims.getExpiration().after(new Date()));

        // Verify expiration is approximately 24 hours from now (within 1 minute
        // tolerance)
        long expirationMs = claims.getExpiration().getTime() - System.currentTimeMillis();
        long expectedMs = 86400000L; // 24 hours in milliseconds
        long toleranceMs = 60000L; // 1 minute tolerance

        assertTrue(Math.abs(expirationMs - expectedMs) < toleranceMs,
                "Expiration should be approximately 24 hours from now");
    }

    @Test
    void testParseToken_InvalidToken_ThrowsException() {
        // Arrange
        String invalidToken = "invalid.jwt.token";

        // Act & Assert
        assertThrows(Exception.class, () -> jwtService.parseToken(invalidToken));
    }

    @Test
    void testParseToken_TamperedToken_ThrowsException() {
        // Arrange
        String email = "test@example.com";
        String token = jwtService.generateToken(email);

        // Tamper with the token by modifying the signature
        String[] parts = token.split("\\.");
        String tamperedToken = parts[0] + "." + parts[1] + ".tampered-signature";

        // Act & Assert
        assertThrows(Exception.class, () -> jwtService.parseToken(tamperedToken));
    }

    @Test
    void testParseToken_ExpiredToken_ThrowsExpiredJwtException() {
        // Arrange: Create an expired token manually
        String email = "test@example.com";
        Date past = new Date(System.currentTimeMillis() - 10000); // 10 seconds ago
        Date morePast = new Date(System.currentTimeMillis() - 20000); // 20 seconds ago

        String expiredToken = Jwts.builder()
                .subject(email)
                .issuedAt(morePast)
                .expiration(past)
                .signWith(SignatureAlgorithm.HS256, testSecret.getBytes(StandardCharsets.UTF_8))
                .compact();

        // Act & Assert
        assertThrows(ExpiredJwtException.class, () -> jwtService.parseToken(expiredToken));
    }

    @Test
    void testParseToken_TokenWithDifferentSecret_ThrowsException() {
        // Arrange: Create token with different secret
        String email = "test@example.com";
        String differentSecret = "different-secret-key-should-fail-validation-here";

        String tokenWithDifferentSecret = Jwts.builder()
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 86400000))
                .signWith(SignatureAlgorithm.HS256, differentSecret.getBytes(StandardCharsets.UTF_8))
                .compact();

        // Act & Assert
        assertThrows(Exception.class, () -> jwtService.parseToken(tokenWithDifferentSecret));
    }

    @Test
    void testGenerateToken_MultipleTokensForSameUser_AreDifferent() {
        // Arrange
        String email = "test@example.com";

        // Act
        String token1 = jwtService.generateToken(email);
        // Delay to ensure different issued-at timestamps (JWT uses seconds precision)
        try {
            Thread.sleep(1100); // 1.1 seconds to ensure different timestamp
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        String token2 = jwtService.generateToken(email);

        // Assert
        assertNotEquals(token1, token2,
                "Tokens generated at different times should be different");

        // Both tokens should be valid and have same subject
        Claims claims1 = jwtService.parseToken(token1);
        Claims claims2 = jwtService.parseToken(token2);

        assertEquals(email, claims1.getSubject());
        assertEquals(email, claims2.getSubject());
    }

    @Test
    void testParseToken_EmptyToken_ThrowsException() {
        // Act & Assert
        assertThrows(Exception.class, () -> jwtService.parseToken(""));
    }

    @Test
    void testParseToken_NullToken_ThrowsException() {
        // Act & Assert
        assertThrows(Exception.class, () -> jwtService.parseToken(null));
    }
}
