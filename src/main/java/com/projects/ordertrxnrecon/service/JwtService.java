package com.projects.ordertrxnrecon.service;

import com.projects.ordertrxnrecon.entity.TokenBlacklist;
import com.projects.ordertrxnrecon.repository.TokenBlacklistRepository;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;
    private final TokenBlacklistRepository tokenBlacklistRepository;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs,
            TokenBlacklistRepository tokenBlacklistRepository) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expirationMs = expirationMs;
        this.tokenBlacklistRepository = tokenBlacklistRepository;
    }

    public String generateToken(String email) {
        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(signingKey)
                .compact();
    }

    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public Date extractExpiration(String token) {
        return parseClaims(token).getExpiration();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        String email = extractEmail(token);
        return email.equals(userDetails.getUsername())
                && !isTokenExpired(token)
                && !isTokenBlacklisted(token);
    }

    public boolean isTokenBlacklisted(String token) {
        String hash = hashToken(token);
        return tokenBlacklistRepository.existsByTokenHash(hash);
    }

    public void blacklistToken(String token) {
        Date expiration = extractExpiration(token);
        String hash = hashToken(token);

        TokenBlacklist blacklistedToken = TokenBlacklist.builder()
                .tokenHash(hash)
                .expiresAt(expiration.toInstant()
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDateTime())
                .build();

        tokenBlacklistRepository.save(blacklistedToken);
    }

    private boolean isTokenExpired(String token) {
        return parseClaims(token).getExpiration().before(new Date());
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
