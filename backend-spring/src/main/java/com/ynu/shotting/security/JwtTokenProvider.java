package com.ynu.shoting.security;

import com.ynu.shoting.config.JwtConfig;
import com.ynu.shoting.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final JwtConfig config;
    private final SecretKey key;

    public JwtTokenProvider(JwtConfig config) {
        this.config = config;
        if (config.getSecret() == null || config.getSecret().getBytes(StandardCharsets.UTF_8).length < 32)
            throw new IllegalStateException("请设置至少32字节的独立 JWT_SECRET；本地联调可使用 integration profile");
        this.key = Keys.hmacShaKeyFor(config.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + config.getExpirationMs());
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("role", user.getRole().name())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserIdFromToken(String token) {
        return Long.parseLong(parseToken(token).getSubject());
    }
}
