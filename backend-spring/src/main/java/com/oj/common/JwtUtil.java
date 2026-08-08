package com.oj.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final Duration expiresIn;

    public JwtUtil(com.oj.config.AppProperties props) {
        String secret = props.getJwt().getSecret();
        // JWT HS256 needs at least 256 bits (32 bytes). Pad if shorter.
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 0, bytes.length);
            bytes = padded;
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expiresIn = parseDuration(props.getJwt().getExpiresIn());
    }

    private Duration parseDuration(String s) {
        if (s == null || s.isBlank()) return Duration.ofDays(7);
        s = s.trim();
        try {
            if (s.endsWith("d")) return Duration.ofDays(Long.parseLong(s.substring(0, s.length() - 1)));
            if (s.endsWith("h")) return Duration.ofHours(Long.parseLong(s.substring(0, s.length() - 1)));
            if (s.endsWith("m")) return Duration.ofMinutes(Long.parseLong(s.substring(0, s.length() - 1)));
            return Duration.ofMillis(Long.parseLong(s));
        } catch (NumberFormatException e) {
            return Duration.ofDays(7);
        }
    }

    public String sign(int id, String username, String role, int tokenVersion) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(id))
                .claim("userId", id)
                .claim("username", username)
                .claim("role", role)
                .claim("tokenVersion", tokenVersion)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiresIn.toMillis()))
                .signWith(key)
                .compact();
    }

    public Claims verify(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
