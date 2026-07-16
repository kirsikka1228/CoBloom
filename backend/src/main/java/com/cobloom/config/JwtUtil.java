package com.cobloom.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {
  private final SecretKey key;
  private final long expireHours;

  public JwtUtil(@Value("${cobloom.jwt-secret}") String secret,
                 @Value("${cobloom.jwt-expire-hours}") long expireHours) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.expireHours = expireHours;
  }

  public String generate(Long userId) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(userId.toString())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(expireHours * 3600)))
        .signWith(key)
        .compact();
  }

  public Long parse(String token) {
    return Long.valueOf(Jwts.parser().verifyWith(key).build()
        .parseSignedClaims(token).getPayload().getSubject());
  }
}
