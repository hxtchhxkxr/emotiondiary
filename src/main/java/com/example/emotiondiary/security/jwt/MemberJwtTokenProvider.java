package com.example.emotiondiary.security.jwt;

import com.example.emotiondiary.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class MemberJwtTokenProvider {
    private final SecretKey key;

    @Getter
    private final long accessExpMin;
    @Getter
    private final long refreshExpMin;

    public MemberJwtTokenProvider(
            @Value("${jwt.member.secret}") String secret,
            @Value("${jwt.member.access-exp-min}") long accessSec,
            @Value("${jwt.member.refresh-exp-min}") long refreshSec) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpMin = accessSec;
        this.refreshExpMin = refreshSec;
    }

    public String createAccessToken(User user) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + accessExpMin * 60 * 1000);
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(now)
                .expiration(exp)
                .signWith(key)
                .compact();
    }

    public String createRefreshToken(User user) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + refreshExpMin * 60 * 1000);
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiration(exp)
                .signWith(key)
                .compact();
    }

    /** 유효성 검증. 만료/위조 시 예외 발생. */
    public Claims parse(String token) {
        try {
            Jws<Claims> jws = Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return jws.getPayload();
        } catch (ExpiredJwtException e) {
            throw e;
        } catch (JwtException e) {
            throw e;
        }
    }

    public Long getUserId(String token) {
        return Long.valueOf(parse(token).getSubject());
    }
}