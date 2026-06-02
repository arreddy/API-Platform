package com.apiplatform.controlplane.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.apiplatform.controlplane.entity.User;
import com.apiplatform.controlplane.exception.GlobalExceptionHandler;
import com.apiplatform.controlplane.repository.UserRepository;
import com.apiplatform.controlplane.service.JwtService;
import com.apiplatform.controlplane.support.WithMockApiPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    classes = {AuthController.class, GlobalExceptionHandler.class})
@ImportAutoConfiguration({
  DispatcherServletAutoConfiguration.class,
  WebMvcAutoConfiguration.class,
  JacksonAutoConfiguration.class,
  HttpMessageConvertersAutoConfiguration.class,
  ValidationAutoConfiguration.class
})
@Import(TestSecurityConfig.class)
class AuthControllerTest {

  @Autowired WebApplicationContext wac;
  final ObjectMapper objectMapper = new ObjectMapper();
  MockMvc mockMvc;

  @MockitoBean UserRepository userRepository;
  @MockitoBean JwtService jwtService;
  @MockitoBean BCryptPasswordEncoder bcrypt;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
  }

  private static final String TENANT = "00000000-0000-0000-0000-000000000001";
  private static final String USER_ID = UUID.randomUUID().toString();

  private User sampleUser() {
    User u = new User();
    u.setId(USER_ID);
    u.setTenantId(TENANT);
    u.setEmail("dev@example.com");
    u.setName("Dev User");
    u.setPasswordHash("$2a$12$hashed");
    u.setRole("admin");
    return u;
  }

  // ---- POST /api/v1/auth/login ----

  @Test
  void login_validCredentials_returns200WithToken() throws Exception {
    when(userRepository.findByEmail("dev@example.com")).thenReturn(Optional.of(sampleUser()));
    when(bcrypt.matches("password123", "$2a$12$hashed")).thenReturn(true);
    when(jwtService.generateToken(USER_ID, TENANT, "admin")).thenReturn("jwt.token.here");

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("email", "dev@example.com", "password", "password123"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value("jwt.token.here"))
        .andExpect(jsonPath("$.user.email").value("dev@example.com"))
        .andExpect(jsonPath("$.user.role").value("admin"));
  }

  @Test
  void login_userNotFound_returns401() throws Exception {
    when(userRepository.findByEmail(any())).thenReturn(Optional.empty());

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("email", "nobody@example.com", "password", "pass1234"))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void login_wrongPassword_returns401() throws Exception {
    when(userRepository.findByEmail(any())).thenReturn(Optional.of(sampleUser()));
    when(bcrypt.matches(any(), any())).thenReturn(false);

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("email", "dev@example.com", "password", "wrongpass"))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void login_invalidEmail_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("email", "not-an-email", "password", "pass1234"))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void login_blankPassword_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("email", "dev@example.com", "password", ""))))
        .andExpect(status().isBadRequest());
  }

  // ---- POST /api/v1/auth/register ----

  @Test
  void register_newUser_returns201WithToken() throws Exception {
    when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
    when(bcrypt.encode(any())).thenReturn("$2a$12$encoded");
    when(userRepository.save(any())).thenAnswer(inv -> {
      User u = inv.getArgument(0);
      u.setId(USER_ID);
      return u;
    });
    when(jwtService.generateToken(any(), any(), any())).thenReturn("jwt.new.token");

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("email", "new@example.com", "password", "securepass", "name", "New User"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.token").value("jwt.new.token"))
        .andExpect(jsonPath("$.user.email").value("new@example.com"));
  }

  @Test
  void register_duplicateEmail_returns409() throws Exception {
    when(userRepository.existsByEmail("dup@example.com")).thenReturn(true);

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("email", "dup@example.com", "password", "securepass", "name", "Dup"))))
        .andExpect(status().isConflict());
  }

  @Test
  void register_shortPassword_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("email", "x@example.com", "password", "short", "name", "X"))))
        .andExpect(status().isBadRequest());
  }

  // ---- GET /api/v1/auth/me ----

  @Test
  @WithMockApiPrincipal
  void me_authenticated_returnsUserInfo() throws Exception {
    when(userRepository.findById(any())).thenReturn(Optional.of(sampleUser()));

    mockMvc
        .perform(get("/api/v1/auth/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("dev@example.com"))
        .andExpect(jsonPath("$.role").value("admin"));
  }
}
