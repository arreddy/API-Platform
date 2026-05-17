package com.apiplatform.controlplane.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.apiplatform.controlplane.dto.ApiDto;
import com.apiplatform.controlplane.dto.PageDto;
import com.apiplatform.controlplane.exception.AppException;
import com.apiplatform.controlplane.service.ApiRegistryService;
import com.apiplatform.controlplane.service.OasAnalysisService;
import com.apiplatform.controlplane.service.OasValidatorService;
import com.apiplatform.controlplane.support.WithMockApiPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ApiController.class)
@Import(TestSecurityConfig.class)
class ApiControllerTest {

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @MockBean ApiRegistryService apiService;
  @MockBean OasAnalysisService oasAnalysisService;

  private static final String TENANT = "00000000-0000-0000-0000-000000000001";
  private static final String API_ID = UUID.randomUUID().toString();

  private ApiDto.Summary sampleSummary() {
    return new ApiDto.Summary(
        API_ID, "pet-api", "Pet API", "1.0.0", "desc", "3.0", List.of("pets"), "active", null, null);
  }

  private ApiDto.Full sampleFull() {
    return new ApiDto.Full(
        API_ID, "pet-api", "Pet API", "1.0.0", "desc", "3.0",
        Map.of("openapi", "3.0.0"), "/v1",
        List.of(Map.of("url", "https://api.example.com/v1")),
        List.of(), Map.of(), List.of("pets"), "active", null, null);
  }

  private ApiDto.RegisterResponse sampleRegisterResponse() {
    return new ApiDto.RegisterResponse(
        sampleSummary(),
        Map.of("targetUrl", "https://api.example.com/v1", "pathPrefix", "/pet-api", "routes", List.of()),
        null);
  }

  // ---- GET /api/v1/apis ----

  @Test
  @WithMockApiPrincipal
  void list_returnsPage() throws Exception {
    var page = new PageDto<>(List.of(sampleSummary()), 1L, 1, 20);
    when(apiService.list(eq(TENANT), any(), any(), eq(1), eq(20))).thenReturn(page);

    mockMvc
        .perform(get("/api/v1/apis"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].name").value("pet-api"))
        .andExpect(jsonPath("$.total").value(1));
  }

  @Test
  @WithMockApiPrincipal
  void list_withSearch_passesParam() throws Exception {
    when(apiService.list(eq(TENANT), any(), eq("pet"), anyInt(), anyInt()))
        .thenReturn(new PageDto<>(List.of(), 0L, 1, 20));

    mockMvc.perform(get("/api/v1/apis").param("search", "pet")).andExpect(status().isOk());
    verify(apiService).list(eq(TENANT), any(), eq("pet"), anyInt(), anyInt());
  }

  // ---- GET /api/v1/apis/{id} ----

  @Test
  @WithMockApiPrincipal
  void get_found_returnsFull() throws Exception {
    when(apiService.get(TENANT, API_ID)).thenReturn(sampleFull());

    mockMvc
        .perform(get("/api/v1/apis/{id}", API_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(API_ID))
        .andExpect(jsonPath("$.name").value("pet-api"));
  }

  @Test
  @WithMockApiPrincipal
  void get_notFound_returns404() throws Exception {
    when(apiService.get(eq(TENANT), any()))
        .thenThrow(new AppException(HttpStatus.NOT_FOUND, "API not found"));

    mockMvc
        .perform(get("/api/v1/apis/{id}", API_ID))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("API not found"));
  }

  // ---- GET /api/v1/apis/{id}/oas ----

  @Test
  @WithMockApiPrincipal
  void getOas_json_returnsMap() throws Exception {
    when(apiService.getOasDocument(TENANT, API_ID))
        .thenReturn(Map.of("openapi", "3.0.0", "info", Map.of("title", "Pet API")));

    mockMvc
        .perform(get("/api/v1/apis/{id}/oas", API_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openapi").value("3.0.0"));
  }

  @Test
  @WithMockApiPrincipal
  void getOas_yaml_returnsYamlContentType() throws Exception {
    when(apiService.getOasDocument(TENANT, API_ID))
        .thenReturn(Map.of("openapi", "3.0.0"));

    mockMvc
        .perform(get("/api/v1/apis/{id}/oas", API_ID).param("format", "yaml"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("application/x-yaml"));
  }

  // ---- POST /api/v1/apis (multipart) ----

  @Test
  @WithMockApiPrincipal
  void register_withFile_returns201() throws Exception {
    when(apiService.register(eq(TENANT), any(), any(), any())).thenReturn(sampleRegisterResponse());

    MockMultipartFile oasFile = new MockMultipartFile(
        "oas", "openapi.json", MediaType.APPLICATION_JSON_VALUE,
        "{\"openapi\":\"3.0.0\"}".getBytes());

    mockMvc
        .perform(multipart("/api/v1/apis").file(oasFile))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.api.name").value("pet-api"));
  }

  @Test
  @WithMockApiPrincipal
  void register_withOasTextParam_returns201() throws Exception {
    when(apiService.register(eq(TENANT), any(), any(), any())).thenReturn(sampleRegisterResponse());

    mockMvc
        .perform(
            multipart("/api/v1/apis")
                .param("oasText", "{\"openapi\":\"3.0.0\",\"info\":{\"title\":\"T\",\"version\":\"1\"},\"paths\":{}}"))
        .andExpect(status().isCreated());
  }

  @Test
  @WithMockApiPrincipal
  void register_noContent_returns400() throws Exception {
    mockMvc
        .perform(multipart("/api/v1/apis"))
        .andExpect(status().isBadRequest());
  }

  // ---- PUT /api/v1/apis/{id} ----

  @Test
  @WithMockApiPrincipal
  void update_returns200() throws Exception {
    when(apiService.update(eq(TENANT), eq(API_ID), any())).thenReturn(sampleFull());

    mockMvc
        .perform(
            put("/api/v1/apis/{id}", API_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("status", "deprecated"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(API_ID));
  }

  // ---- DELETE /api/v1/apis/{id} ----

  @Test
  @WithMockApiPrincipal
  void delete_returns204() throws Exception {
    doNothing().when(apiService).delete(TENANT, API_ID);

    mockMvc
        .perform(delete("/api/v1/apis/{id}", API_ID))
        .andExpect(status().isNoContent());
  }

  // ---- POST /api/v1/apis/validate ----

  @Test
  @WithMockApiPrincipal
  void validate_validOas_returnsValidTrue() throws Exception {
    var parsed = new OasValidatorService.ParsedOas(
        "3.0", "Pet API", "1.0.0", null, null, List.of(),
        List.of(Map.of("method", "GET", "path", "/pets")), Map.of(), List.of("pets"), Map.of());
    when(apiService.validate(any())).thenReturn(parsed);

    mockMvc
        .perform(
            multipart("/api/v1/apis/validate")
                .param("oasText", "{\"openapi\":\"3.0.0\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.valid").value(true))
        .andExpect(jsonPath("$.summary.title").value("Pet API"))
        .andExpect(jsonPath("$.summary.endpointCount").value(1));
  }
}
