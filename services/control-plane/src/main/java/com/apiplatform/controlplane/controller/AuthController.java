package com.apiplatform.controlplane.controller;

import com.apiplatform.controlplane.entity.User;
import com.apiplatform.controlplane.exception.AppException;
import com.apiplatform.controlplane.repository.UserRepository;
import com.apiplatform.controlplane.security.ApiPrincipal;
import com.apiplatform.controlplane.service.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder bcrypt;

    private static final String DEFAULT_TENANT = "00000000-0000-0000-0000-000000000001";

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8) String password,
            @NotBlank String name) {}

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest req) {
        User user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!bcrypt.matches(req.password(), user.getPasswordHash())) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        String token = jwtService.generateToken(user.getId(), user.getTenantId(), user.getRole());
        return ResponseEntity.ok(Map.of(
                "token", token,
                "user", Map.of("id", user.getId(), "email", user.getEmail(),
                        "name", user.getName(), "role", user.getRole())));
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new AppException(HttpStatus.CONFLICT, "Email already registered");
        }

        User user = User.builder()
                .tenantId(DEFAULT_TENANT)
                .email(req.email())
                .name(req.name())
                .passwordHash(bcrypt.encode(req.password()))
                .role("developer")
                .build();
        userRepository.save(user);

        String token = jwtService.generateToken(user.getId(), user.getTenantId(), user.getRole());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "token", token,
                "user", Map.of("id", user.getId(), "email", user.getEmail(),
                        "name", user.getName(), "role", user.getRole())));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(@AuthenticationPrincipal ApiPrincipal principal) {
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));
        return ResponseEntity.ok(Map.of("id", user.getId(), "email", user.getEmail(),
                "name", user.getName(), "role", user.getRole(), "tenantId", user.getTenantId()));
    }
}
