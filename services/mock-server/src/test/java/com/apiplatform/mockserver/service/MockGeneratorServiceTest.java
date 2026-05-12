package com.apiplatform.mockserver.service;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MockGeneratorServiceTest {

  private MockGeneratorService service;
  private ObjectMapper objectMapper;

  // Minimal OAS 3.0 document with various schema types
  private static final String OAS_JSON =
      """
      {
        "openapi": "3.0.0",
        "info": { "title": "Test API", "version": "1.0.0" },
        "paths": {
          "/pets": {
            "get": {
              "operationId": "listPets",
              "responses": {
                "200": {
                  "description": "ok",
                  "content": {
                    "application/json": {
                      "schema": {
                        "type": "array",
                        "items": {
                          "type": "object",
                          "properties": {
                            "id":   { "type": "integer" },
                            "name": { "type": "string" },
                            "age":  { "type": "number" },
                            "active": { "type": "boolean" }
                          }
                        }
                      }
                    }
                  }
                }
              }
            },
            "post": {
              "responses": {
                "201": { "description": "created", "content": { "application/json": {} } }
              }
            }
          },
          "/pets/{id}": {
            "get": {
              "responses": {
                "200": {
                  "description": "a pet",
                  "content": {
                    "application/json": {
                      "schema": {
                        "type": "object",
                        "properties": {
                          "id":     { "type": "integer" },
                          "name":   { "type": "string", "format": "email" },
                          "tag":    { "type": "string", "enum": ["cat", "dog", "fish"] }
                        }
                      }
                    }
                  }
                }
              }
            }
          },
          "/status": {
            "get": {
              "responses": {
                "200": {
                  "description": "ok",
                  "content": {
                    "application/json": {
                      "example": { "status": "running", "uptime": 99 }
                    }
                  }
                }
              }
            }
          },
          "/formats": {
            "get": {
              "responses": {
                "200": {
                  "description": "ok",
                  "content": {
                    "application/json": {
                      "schema": {
                        "type": "object",
                        "properties": {
                          "uuid":     { "type": "string", "format": "uuid" },
                          "date":     { "type": "string", "format": "date" },
                          "dateTime": { "type": "string", "format": "date-time" },
                          "email":    { "type": "string", "format": "email" },
                          "uri":      { "type": "string", "format": "uri" },
                          "ipv4":     { "type": "string", "format": "ipv4" },
                          "ipv6":     { "type": "string", "format": "ipv6" },
                          "password": { "type": "string", "format": "password" },
                          "hostname": { "type": "string", "format": "hostname" },
                          "byte":     { "type": "string", "format": "byte" }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
      """;

  @BeforeEach
  void setUp() {
    service = new MockGeneratorService();
    objectMapper = new ObjectMapper();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> parseOas() {
    try {
      return objectMapper.readValue(OAS_JSON, Map.class);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  // ---- path not found ----

  @Test
  void generate_unknownPath_returns404() {
    var result = service.generate(parseOas(), "GET", "/unknown", 200);
    assertThat(result.status()).isEqualTo(404);
  }

  @Test
  void generate_nullOas_returns404() {
    var result = service.generate(Map.of(), "GET", "/pets", 200);
    assertThat(result.status()).isEqualTo(404);
  }

  // ---- method not allowed ----

  @Test
  void generate_wrongMethod_returns405() {
    var result = service.generate(parseOas(), "DELETE", "/pets", 200);
    assertThat(result.status()).isEqualTo(405);
  }

  // ---- array schema ----

  @Test
  void generate_arraySchema_returnsList() {
    var result = service.generate(parseOas(), "GET", "/pets", 200);
    assertThat(result.status()).isEqualTo(200);
    assertThat(result.body()).isInstanceOf(List.class);
  }

  @Test
  void generate_arrayItems_haveObjectFields() {
    var result = service.generate(parseOas(), "GET", "/pets", 200);
    @SuppressWarnings("unchecked")
    List<Object> list = (List<Object>) result.body();
    assertThat(list).isNotEmpty();
    @SuppressWarnings("unchecked")
    Map<String, Object> item = (Map<String, Object>) list.get(0);
    assertThat(item).containsKey("id");
    assertThat(item).containsKey("name");
  }

  // ---- object schema ----

  @Test
  void generate_objectSchema_returnsMap() {
    var result = service.generate(parseOas(), "GET", "/pets/1", 200);
    assertThat(result.status()).isEqualTo(200);
    assertThat(result.body()).isInstanceOf(Map.class);
  }

  @Test
  void generate_enumField_returnsFirstValue() {
    var result = service.generate(parseOas(), "GET", "/pets/1", 200);
    @SuppressWarnings("unchecked")
    Map<String, Object> body = (Map<String, Object>) result.body();
    assertThat(body.get("tag")).isEqualTo("cat");
  }

  // ---- explicit example ----

  @Test
  void generate_withInlineExample_returnsExample() {
    var result = service.generate(parseOas(), "GET", "/status", 200);
    assertThat(result.status()).isEqualTo(200);
    // OAS parser may return ObjectNode instead of plain Map — convert to string for assertion
    String bodyStr = result.body().toString();
    assertThat(bodyStr).contains("running");
    assertThat(bodyStr).contains("99");
  }

  // ---- path template matching ----

  @Test
  void generate_pathTemplate_matchesConcreteId() {
    var result = service.generate(parseOas(), "GET", "/pets/42", 200);
    assertThat(result.status()).isEqualTo(200);
    assertThat(result.body()).isInstanceOf(Map.class);
  }

  @Test
  void generate_pathTemplate_deepPath_notFound() {
    var result = service.generate(parseOas(), "GET", "/pets/42/extra/segment", 200);
    assertThat(result.status()).isEqualTo(404);
  }

  // ---- format-based string generation ----

  @Test
  void generate_formatFields_allPopulated() {
    var result = service.generate(parseOas(), "GET", "/formats", 200);
    assertThat(result.status()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    Map<String, Object> body = (Map<String, Object>) result.body();

    assertThat(body.get("uuid")).asString().matches(
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    assertThat(body.get("email")).asString().contains("@");
    assertThat(body.get("date")).asString().matches("\\d{4}-\\d{2}-\\d{2}");
    assertThat(body.get("dateTime")).asString().isNotBlank();
    assertThat(body.get("uri")).asString().isNotBlank();
    assertThat(body.get("ipv4")).asString().matches("\\d+\\.\\d+\\.\\d+\\.\\d+");
    assertThat(body.get("password")).asString().isNotBlank();
    assertThat(body.get("byte")).asString().isNotBlank();
  }

  // ---- content type ----

  @Test
  void generate_jsonContent_returnsJsonContentType() {
    var result = service.generate(parseOas(), "GET", "/pets", 200);
    assertThat(result.contentType()).isEqualTo("application/json");
  }

  // ---- fallback status code ----

  @Test
  void generate_nonMatchingStatusCode_fallsBackToFirstResponse() {
    var result = service.generate(parseOas(), "GET", "/pets", 404);
    // The OAS has 200 only — falls back to first available response
    assertThat(result.status()).isEqualTo(404);
    assertThat(result.body()).isNotNull();
  }

  // ---- POST with empty schema ----

  @Test
  void generate_postWithNoSchema_returnsEmptyBody() {
    var result = service.generate(parseOas(), "POST", "/pets", 201);
    assertThat(result.status()).isEqualTo(201);
    assertThat(result.body()).isInstanceOf(Map.class);
  }
}
