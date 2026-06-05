package com.apiplatform.controlplane.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.apiplatform.controlplane.dto.PageDto;
import com.apiplatform.controlplane.dto.ProxyDto;
import com.apiplatform.controlplane.entity.Proxy;
import com.apiplatform.controlplane.exception.AppException;
import com.apiplatform.controlplane.exception.GlobalExceptionHandler;
import com.apiplatform.controlplane.service.ProxyService;
import com.apiplatform.controlplane.support.WithMockApiPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    classes = {ProxyController.class, GlobalExceptionHandler.class},
    properties = "app.internal-token=internal-dev-token")
@ImportAutoConfiguration({
  DispatcherServletAutoConfiguration.class,
  WebMvcAutoConfiguration.class,
  JacksonAutoConfiguration.class,
  HttpMessageConvertersAutoConfiguration.class,
  ValidationAutoConfiguration.class
})
@Import(TestSecurityConfig.class)
class ProxyControllerTest {

  @Autowired WebApplicationContext wac;
  final ObjectMapper objectMapper = new ObjectMapper();
  MockMvc mockMvc;

  @MockitoBean ProxyService proxyService;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
  }

  private static final String TENANT = "00000000-0000-0000-0000-000000000001";
  private static final String PROXY_ID = UUID.randomUUID().toString();

  private ProxyDto.Summary sampleSummary() {
    return new ProxyDto.Summary(
        PROXY_ID, null, "petstore", "desc",
        "https://petstore3.swagger.io/api/v3", "/petstore", 1, "active", null, null);
  }

  private ProxyDto.Full sampleFull() {
    return new ProxyDto.Full(
        PROXY_ID, TENANT, null, "petstore", "desc",
        "https://petstore3.swagger.io/api/v3", "/petstore", true, 1,
        Map.of(), List.of(), Map.of(), "active", null, null);
  }

  // ---- GET /api/v1/proxies ----

  @Test
  @WithMockApiPrincipal
  void list_returnsPage() throws Exception {
    var page = new PageDto<>(List.of(sampleSummary()), 1L, 1, 20);
    when(proxyService.list(eq(TENANT), any(), any(), eq(1), eq(20))).thenReturn(page);

    mockMvc
        .perform(get("/api/v1/proxies"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].name").value("petstore"))
        .andExpect(jsonPath("$.total").value(1));
  }

  @Test
  @WithMockApiPrincipal
  void list_withStatusParam_passesFilter() throws Exception {
    when(proxyService.list(eq(TENANT), eq("active"), any(), anyInt(), anyInt()))
        .thenReturn(new PageDto<>(List.of(), 0L, 1, 20));

    mockMvc
        .perform(get("/api/v1/proxies").param("status", "active"))
        .andExpect(status().isOk());

    verify(proxyService).list(eq(TENANT), eq("active"), any(), anyInt(), anyInt());
  }

  // ---- GET /api/v1/proxies/{id} ----

  @Test
  @WithMockApiPrincipal
  void get_found_returnsFull() throws Exception {
    when(proxyService.get(TENANT, PROXY_ID)).thenReturn(sampleFull());

    mockMvc
        .perform(get("/api/v1/proxies/{id}", PROXY_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(PROXY_ID))
        .andExpect(jsonPath("$.pathPrefix").value("/petstore"));
  }

  @Test
  @WithMockApiPrincipal
  void get_notFound_returns404() throws Exception {
    when(proxyService.get(eq(TENANT), any()))
        .thenThrow(new AppException(HttpStatus.NOT_FOUND, "Proxy not found"));

    mockMvc
        .perform(get("/api/v1/proxies/{id}", PROXY_ID))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("Proxy not found"));
  }

  // ---- POST /api/v1/proxies ----

  @Test
  @WithMockApiPrincipal
  void create_validRequest_returns201() throws Exception {
    when(proxyService.create(eq(TENANT), any(), any())).thenReturn(sampleFull());

    var req = Map.of(
        "name", "petstore",
        "targetUrl", "https://petstore3.swagger.io/api/v3",
        "pathPrefix", "/petstore");

    mockMvc
        .perform(
            post("/api/v1/proxies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("petstore"));
  }

  @Test
  @WithMockApiPrincipal
  void create_missingName_returns400() throws Exception {
    var req = Map.of("targetUrl", "https://example.com", "pathPrefix", "/x");

    mockMvc
        .perform(
            post("/api/v1/proxies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isBadRequest());
  }

  // ---- PUT /api/v1/proxies/{id} ----

  @Test
  @WithMockApiPrincipal
  void update_validRequest_returns200() throws Exception {
    when(proxyService.update(eq(TENANT), eq(PROXY_ID), any(), any())).thenReturn(sampleFull());

    var req = Map.of("name", "updated-name");
    mockMvc
        .perform(
            put("/api/v1/proxies/{id}", PROXY_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(PROXY_ID));
  }

  // ---- DELETE /api/v1/proxies/{id} ----

  @Test
  @WithMockApiPrincipal
  void delete_returns204() throws Exception {
    doNothing().when(proxyService).delete(TENANT, PROXY_ID);

    mockMvc
        .perform(delete("/api/v1/proxies/{id}", PROXY_ID))
        .andExpect(status().isNoContent());
  }

  // ---- GET /api/v1/proxies/{id}/versions ----

  @Test
  @WithMockApiPrincipal
  void versions_returnsVersionList() throws Exception {
    var versionSummary = new ProxyDto.VersionSummary(UUID.randomUUID().toString(), 1, "Initial", null);
    when(proxyService.getVersions(TENANT, PROXY_ID)).thenReturn(List.of(versionSummary));

    mockMvc
        .perform(get("/api/v1/proxies/{id}/versions", PROXY_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].version").value(1));
  }

  // ---- POST /api/v1/proxies/{id}/rollback/{version} ----

  @Test
  @WithMockApiPrincipal
  void rollback_returns200() throws Exception {
    when(proxyService.rollback(eq(TENANT), eq(PROXY_ID), any(), eq(1))).thenReturn(sampleFull());

    mockMvc
        .perform(post("/api/v1/proxies/{id}/rollback/1", PROXY_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1));
  }

  // ---- GET /api/v1/proxies/_internal/active ----

  @Test
  void activeProxies_validToken_returnsList() throws Exception {
    Proxy proxy = new Proxy();
    proxy.setId(PROXY_ID);
    proxy.setName("petstore");
    proxy.setStatus("active");
    when(proxyService.getAllActive()).thenReturn(List.of(proxy));

    mockMvc
        .perform(
            get("/api/v1/proxies/_internal/active")
                .header("X-Internal-Token", "internal-dev-token"))
        .andExpect(status().isOk());
  }

  @Test
  void activeProxies_invalidToken_returns403() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/proxies/_internal/active")
                .header("X-Internal-Token", "definitely-wrong-token"))
        .andExpect(status().isForbidden());
  }
}
