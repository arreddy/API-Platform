package com.apiplatform.controlplane.controller;

import com.apiplatform.controlplane.service.JwtService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@TestConfiguration
class TestSecurityConfig {

  @Bean
  SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
    http.csrf(c -> c.disable()).authorizeHttpRequests(a -> a.anyRequest().permitAll());
    return http.build();
  }

  // Satisfies JwtAuthFilter's dependency — @MockBean in test class overrides this
  @Bean
  JwtService jwtService() {
    return new JwtService("test-webmvc-secret-for-controller-tests-only", 3_600_000L);
  }
}
