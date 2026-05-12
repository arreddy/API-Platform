package com.apiplatform.controlplane.service;

import static org.assertj.core.api.Assertions.*;

import com.apiplatform.controlplane.exception.AppException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class OasValidatorServiceTest {

  private OasValidatorService validator;

  private static final String OAS_JSON =
      """
      {
        "openapi": "3.0.0",
        "info": {
          "title": "Pet API",
          "version": "2.0.0",
          "description": "A pet store"
        },
        "servers": [{ "url": "https://api.example.com/v2", "description": "Prod" }],
        "paths": {
          "/pets": {
            "get": {
              "operationId": "listPets",
              "summary": "List pets",
              "tags": ["pets"],
              "responses": { "200": { "description": "ok" } }
            },
            "post": {
              "operationId": "createPet",
              "tags": ["pets"],
              "responses": { "201": { "description": "created" } }
            }
          },
          "/pets/{id}": {
            "get": {
              "operationId": "getPet",
              "tags": ["pets"],
              "responses": { "200": { "description": "ok" } }
            },
            "delete": {
              "operationId": "deletePet",
              "tags": ["pets"],
              "responses": { "204": { "description": "deleted" } }
            }
          }
        },
        "components": {
          "securitySchemes": {
            "bearerAuth": {
              "type": "http",
              "scheme": "bearer"
            }
          }
        }
      }
      """;

  private static final String OAS_YAML =
      """
      openapi: "3.0.0"
      info:
        title: YAML API
        version: "1.0.0"
      paths:
        /items:
          get:
            responses:
              "200":
                description: ok
      """;

  private static final String OAS_NO_SERVER =
      """
      {
        "openapi": "3.0.0",
        "info": { "title": "No Server", "version": "1.0.0" },
        "paths": {}
      }
      """;

  @BeforeEach
  void setUp() {
    validator = new OasValidatorService(new ObjectMapper());
  }

  @Test
  void parseAndValidate_validJson_returnsTitle() {
    var result = validator.parseAndValidate(OAS_JSON);
    assertThat(result.title()).isEqualTo("Pet API");
  }

  @Test
  void parseAndValidate_validJson_returnsVersion() {
    var result = validator.parseAndValidate(OAS_JSON);
    assertThat(result.version()).isEqualTo("2.0.0");
  }

  @Test
  void parseAndValidate_validJson_returnsDescription() {
    var result = validator.parseAndValidate(OAS_JSON);
    assertThat(result.description()).isEqualTo("A pet store");
  }

  @Test
  void parseAndValidate_validJson_returnsOasVersion() {
    var result = validator.parseAndValidate(OAS_JSON);
    assertThat(result.oasVersion()).startsWith("3.");
  }

  @Test
  void parseAndValidate_validJson_extractsFourEndpoints() {
    var result = validator.parseAndValidate(OAS_JSON);
    assertThat(result.endpoints()).hasSize(4);
  }

  @Test
  void parseAndValidate_validJson_extractsMethods() {
    var result = validator.parseAndValidate(OAS_JSON);
    List<String> methods = result.endpoints().stream().map(e -> (String) e.get("method")).toList();
    assertThat(methods).containsExactlyInAnyOrder("GET", "POST", "GET", "DELETE");
  }

  @Test
  void parseAndValidate_validJson_extractsTags() {
    var result = validator.parseAndValidate(OAS_JSON);
    assertThat(result.tags()).containsExactly("pets");
  }

  @Test
  void parseAndValidate_validJson_extractsServer() {
    var result = validator.parseAndValidate(OAS_JSON);
    assertThat(result.servers()).hasSize(1);
    assertThat(result.servers().get(0).get("url")).isEqualTo("https://api.example.com/v2");
  }

  @Test
  void parseAndValidate_serverWithPath_extractsBasePath() {
    var result = validator.parseAndValidate(OAS_JSON);
    assertThat(result.basePath()).isEqualTo("/v2");
  }

  @Test
  void parseAndValidate_validJson_extractsSecuritySchemes() {
    var result = validator.parseAndValidate(OAS_JSON);
    assertThat(result.securitySchemes()).containsKey("bearerAuth");
  }

  @Test
  void parseAndValidate_validJson_rawDocumentNotNull() {
    var result = validator.parseAndValidate(OAS_JSON);
    assertThat(result.rawDocument()).isNotNull().containsKey("openapi");
  }

  @Test
  void parseAndValidate_validYaml_returnsTitle() {
    var result = validator.parseAndValidate(OAS_YAML);
    assertThat(result.title()).isEqualTo("YAML API");
  }

  @Test
  void parseAndValidate_noServers_basePathNull() {
    var result = validator.parseAndValidate(OAS_NO_SERVER);
    assertThat(result.basePath()).isNull();
  }

  @Test
  void parseAndValidate_missingInfoBlock_throwsAppException() {
    // Valid JSON but not a valid OAS document (missing required info/openapi fields)
    assertThatThrownBy(() -> validator.parseAndValidate("{\"someKey\": \"someValue\"}"))
        .isInstanceOf(AppException.class)
        .extracting(ex -> ((AppException) ex).getStatus())
        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
  }

  @Test
  void parseAndValidate_emptyJson_throwsAppException() {
    // Empty JSON object — OAS parser returns null OpenAPI
    assertThatThrownBy(() -> validator.parseAndValidate("{}"))
        .isInstanceOf(AppException.class);
  }

  @Test
  void parseAndValidate_endpointsHavePaths() {
    var result = validator.parseAndValidate(OAS_JSON);
    assertThat(result.endpoints())
        .extracting(e -> e.get("path"))
        .containsOnly("/pets", "/pets", "/pets/{id}", "/pets/{id}");
  }

  @Test
  void parseAndValidate_endpointsHaveOperationIds() {
    var result = validator.parseAndValidate(OAS_JSON);
    assertThat(result.endpoints())
        .extracting(e -> e.get("operationId"))
        .containsExactlyInAnyOrder("listPets", "createPet", "getPet", "deletePet");
  }
}
