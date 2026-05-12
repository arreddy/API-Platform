package com.apiplatform.controlplane.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.time.Instant;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private final Algorithm algorithm;
  private final long expirationMs;

  public JwtService(
      @Value("${app.jwt-secret}") String secret,
      @Value("${app.jwt-expiration-ms:86400000}") long expirationMs) {
    this.algorithm = Algorithm.HMAC256(secret);
    this.expirationMs = expirationMs;
  }

  public String generateToken(String userId, String tenantId, String role) {
    return JWT.create()
        .withSubject(userId)
        .withClaim("tenant_id", tenantId)
        .withClaim("role", role)
        .withIssuedAt(new Date())
        .withExpiresAt(new Date(Instant.now().toEpochMilli() + expirationMs))
        .sign(algorithm);
  }

  public DecodedJWT verify(String token) throws JWTVerificationException {
    return JWT.require(algorithm).build().verify(token);
  }

  public record Claims(String userId, String tenantId, String role) {}

  public Claims extractClaims(String token) {
    DecodedJWT jwt = verify(token);
    return new Claims(
        jwt.getSubject(), jwt.getClaim("tenant_id").asString(), jwt.getClaim("role").asString());
  }
}
