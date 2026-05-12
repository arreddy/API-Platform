package com.apiplatform.controlplane.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.apiplatform.controlplane.service.JwtService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthFilterTest {

  // Use a real JwtService — avoids Mockito inline-mock limitations on Java 25
  private final JwtService jwtService =
      new JwtService("filter-test-secret-for-jwt-tests-only-12345", 3_600_000L);
  private JwtAuthFilter filter;

  private MockHttpServletRequest request;
  private MockHttpServletResponse response;
  private FilterChain chain;

  @BeforeEach
  void setUp() {
    filter = new JwtAuthFilter(jwtService);
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
    chain = mock(FilterChain.class);
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void noAuthHeader_continuesChainWithoutAuth() throws Exception {
    filter.doFilterInternal(request, response, chain);

    verify(chain).doFilter(request, response);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void nonBearerHeader_continuesChainWithoutAuth() throws Exception {
    request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
    filter.doFilterInternal(request, response, chain);

    verify(chain).doFilter(request, response);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void validBearerToken_setsAuthentication() throws Exception {
    String token = jwtService.generateToken("user-1", "tenant-1", "admin");
    request.addHeader("Authorization", "Bearer " + token);

    filter.doFilterInternal(request, response, chain);

    verify(chain).doFilter(request, response);
    var auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth).isNotNull();
    assertThat(auth.getPrincipal()).isInstanceOf(ApiPrincipal.class);
    ApiPrincipal principal = (ApiPrincipal) auth.getPrincipal();
    assertThat(principal.userId()).isEqualTo("user-1");
    assertThat(principal.tenantId()).isEqualTo("tenant-1");
    assertThat(principal.role()).isEqualTo("admin");
  }

  @Test
  void invalidToken_continuesChainWithoutAuth() throws Exception {
    request.addHeader("Authorization", "Bearer this.is.not.valid");
    filter.doFilterInternal(request, response, chain);

    verify(chain).doFilter(request, response);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void expiredToken_continuesChainWithoutAuth() throws Exception {
    JwtService expiredJwt =
        new JwtService("filter-test-secret-for-jwt-tests-only-12345", -5000L);
    String token = expiredJwt.generateToken("u", "t", "admin");
    request.addHeader("Authorization", "Bearer " + token);

    filter.doFilterInternal(request, response, chain);

    verify(chain).doFilter(request, response);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void validToken_setsRoleAuthority() throws Exception {
    String token = jwtService.generateToken("u", "t", "developer");
    request.addHeader("Authorization", "Bearer " + token);

    filter.doFilterInternal(request, response, chain);

    var auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth.getAuthorities())
        .extracting(a -> a.getAuthority())
        .containsExactly("ROLE_DEVELOPER");
  }

  @Test
  void existingAuthentication_notOverwritten() throws Exception {
    var existing =
        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            new ApiPrincipal("existing", "t", "admin"), null, java.util.List.of());
    SecurityContextHolder.getContext().setAuthentication(existing);

    String token = jwtService.generateToken("new-user", "t", "admin");
    request.addHeader("Authorization", "Bearer " + token);
    filter.doFilterInternal(request, response, chain);

    var auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(((ApiPrincipal) auth.getPrincipal()).userId()).isEqualTo("existing");
  }
}
