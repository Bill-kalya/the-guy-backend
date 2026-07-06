package com.theguy.app.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private static JwtUtil jwtUtil;
    private static final String testUserId = "550e8400-e29b-41d4-a716-446655440000";
    private static final String SECRET = "test-secret-key-that-is-long-enough-for-hs256-algo!!";

    @BeforeAll
    static void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expiration", 86400000L);
        ReflectionTestUtils.setField(jwtUtil, "refreshExpiration", 604800000L);
    }

    @Test
    void shouldGenerateAndValidateToken() {
        String token = jwtUtil.generateToken(testUserId, Map.of("role", "CUSTOMER"));
        assertThat(token).isNotNull();

        String extractedUserId = jwtUtil.extractUserId(token);
        assertThat(extractedUserId).isEqualTo(testUserId);

        boolean valid = jwtUtil.validateToken(token, testUserId);
        assertThat(valid).isTrue();
    }

    @Test
    void shouldExtractTokenId() {
        String token = jwtUtil.generateToken(testUserId, Map.of("role", "CUSTOMER"));
        String jti = jwtUtil.getTokenId(token);
        assertThat(jti).isNotNull();
        assertThat(jti.length()).isGreaterThan(0);
    }

    @Test
    void shouldGenerateRefreshToken() {
        String refreshToken = jwtUtil.generateRefreshToken(testUserId);
        assertThat(refreshToken).isNotNull();

        String extractedUserId = jwtUtil.extractUserId(refreshToken);
        assertThat(extractedUserId).isEqualTo(testUserId);
    }

    @Test
    void shouldRejectExpiredToken() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String expiredToken = Jwts.builder()
            .subject(testUserId)
            .issuedAt(new Date(System.currentTimeMillis() - 86400000L * 2))
            .expiration(new Date(System.currentTimeMillis() - 86400000L))
            .claim("role", "CUSTOMER")
            .signWith(key)
            .compact();

        boolean valid = jwtUtil.validateToken(expiredToken, testUserId);
        assertThat(valid).isFalse();
    }

    @Test
    void shouldRejectTokenWithWrongUser() {
        String token = jwtUtil.generateToken(testUserId, Map.of("role", "CUSTOMER"));
        boolean valid = jwtUtil.validateToken(token, "wrong-user-id");
        assertThat(valid).isFalse();
    }

    @Test
    void shouldGetClaimsFromToken() {
        String token = jwtUtil.generateToken(testUserId, Map.of("role", "CUSTOMER"));
        Claims claims = jwtUtil.getClaimsFromToken(token);
        assertThat(claims.get("role")).isEqualTo("CUSTOMER");
    }

    @Test
    void shouldExtractExpiration() {
        String token = jwtUtil.generateToken(testUserId, Map.of("role", "CUSTOMER"));
        Date expiration = jwtUtil.extractExpiration(token);
        assertThat(expiration).isAfter(new Date());
    }
}
