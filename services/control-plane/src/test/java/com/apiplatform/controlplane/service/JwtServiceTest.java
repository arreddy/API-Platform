package com.apiplatform.controlplane.service;

import static org.assertj.core.api.Assertions.*;

import com.auth0.jwt.exceptions.JWTVerificationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  private JwtService jwtService;
  private static final String SECRET = "a-very-long-secret-key-used-only-in-tests-12345";

  @BeforeEach
  void setUp() {
    jwtService = new JwtService(SECRET, 3_600_000L);
  }

  @Test
  void generateAndExtract_returnsSameClaims() {
    String token = jwtService.generateToken("user-1", "tenant-1", "admin");
    JwtService.Claims claims = jwtService.extractClaims(token);

    assertThat(claims.userId()).isEqualTo("user-1");
    assertThat(claims.tenantId()).isEqualTo("tenant-1");
    assertThat(claims.role()).isEqualTo("admin");
  }

  @Test
  void verify_withValidToken_succeeds() {
    String token = jwtService.generateToken("u", "t", "developer");
    assertThatCode(() -> jwtService.verify(token)).doesNotThrowAnyException();
  }

  @Test
  void verify_withTamperedToken_throws() {
    String token = jwtService.generateToken("u", "t", "admin") + "tampered";
    assertThatThrownBy(() -> jwtService.verify(token))
        .isInstanceOf(JWTVerificationException.class);
  }

  @Test
  void verify_withExpiredToken_throws() {
    JwtService expired = new JwtService(SECRET, -1000L);
    String token = expired.generateToken("u", "t", "admin");
    assertThatThrownBy(() -> jwtService.verify(token))
        .isInstanceOf(JWTVerificationException.class);
  }

  @Test
  void verify_withWrongSecret_throws() {
    JwtService other = new JwtService("completely-different-secret-key-56789", 3_600_000L);
    String token = other.generateToken("u", "t", "admin");
    assertThatThrownBy(() -> jwtService.verify(token))
        .isInstanceOf(JWTVerificationException.class);
  }

  @Test
  void generateToken_differsByUser() {
    String t1 = jwtService.generateToken("user-1", "tenant", "admin");
    String t2 = jwtService.generateToken("user-2", "tenant", "admin");
    assertThat(t1).isNotEqualTo(t2);
  }

  @Test
  void extractClaims_withInvalidToken_throws() {
    assertThatThrownBy(() -> jwtService.extractClaims("not.a.jwt"))
        .isInstanceOf(JWTVerificationException.class);
  }
}
