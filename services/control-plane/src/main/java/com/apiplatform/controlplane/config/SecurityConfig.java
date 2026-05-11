package com.apiplatform.controlplane.config;

import com.apiplatform.controlplane.security.ApiPrincipal;
import com.apiplatform.controlplane.security.JwtAuthFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Value("${app.cors-origin:*}")
    private String corsOrigin;

    @Value("${app.dev-mode:true}")
    private boolean devMode;

    private static final String DEFAULT_TENANT = "00000000-0000-0000-0000-000000000001";
    private static final String DEFAULT_DEV_USER = "00000000-0000-0000-0000-000000000002";

    @Bean
    public BCryptPasswordEncoder bcryptPasswordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(c -> c.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/health", "/actuator/**").permitAll()
                .requestMatchers("/api/v1/auth/**").permitAll()
                // Internal endpoints validated by header not JWT
                .requestMatchers("/api/v1/proxies/_internal/**").permitAll()
                .requestMatchers("/api/v1/keys/_internal/**").permitAll()
                .requestMatchers("/api/v1/analytics/_internal/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        // Dev mode — inject default admin principal when no auth present
        if (devMode) {
            http.addFilterBefore((req, res, chain) -> {
                var httpReq = (jakarta.servlet.http.HttpServletRequest) req;
                if (SecurityContextHolder.getContext().getAuthentication() == null
                        && httpReq.getHeader("Authorization") == null) {
                    ApiPrincipal principal = new ApiPrincipal(DEFAULT_DEV_USER, DEFAULT_TENANT, "admin");
                    var auth = new UsernamePasswordAuthenticationToken(
                            principal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
                chain.doFilter(req, res);
            }, UsernamePasswordAuthenticationFilter.class);
        }

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(corsOrigin.equals("*") ? "*" : corsOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(!corsOrigin.equals("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
