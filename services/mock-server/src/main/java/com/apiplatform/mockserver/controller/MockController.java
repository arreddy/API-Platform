package com.apiplatform.mockserver.controller;

import com.apiplatform.mockserver.service.ControlPlaneClient;
import com.apiplatform.mockserver.service.MockGeneratorService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class MockController {

  private final MockGeneratorService generator;
  private final ControlPlaneClient controlPlaneClient;

  @GetMapping("/health")
  public Map<String, String> health() {
    return Map.of("status", "ok", "service", "mock-server");
  }

  /**
   * Mock any path under /mock/{proxyId}/** Optional query param ?__status=201 to control response
   * status.
   */
  @RequestMapping("/mock/{proxyId}/**")
  public ResponseEntity<Object> mockProxy(
      @PathVariable String proxyId,
      @RequestParam(value = "__status", defaultValue = "200") int statusCode,
      jakarta.servlet.http.HttpServletRequest request) {

    Map<String, Object> oasDoc = controlPlaneClient.fetchOasForProxy(proxyId);
    if (oasDoc == null) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(Map.of("error", "No OAS document found for proxy: " + proxyId));
    }

    // Extract path after /mock/{proxyId}
    String fullPath = request.getRequestURI();
    String prefix = "/mock/" + proxyId;
    String mockPath = fullPath.startsWith(prefix) ? fullPath.substring(prefix.length()) : "/";
    if (mockPath.isEmpty()) mockPath = "/";

    MockGeneratorService.MockResponse result =
        generator.generate(oasDoc, request.getMethod(), mockPath, statusCode);

    return ResponseEntity.status(result.status())
        .contentType(MediaType.parseMediaType(result.contentType()))
        .header("X-Mock-Response", "true")
        .body(result.body());
  }

  /** Inline mock — caller provides OAS document, path, method, and optional status. */
  @PostMapping("/mock/inline")
  public ResponseEntity<Object> mockInline(@RequestBody InlineRequest req) {
    if (req.oasDocument() == null || req.path() == null || req.method() == null) {
      return ResponseEntity.badRequest()
          .body(Map.of("error", "oasDocument, path, and method are required"));
    }

    int status = req.statusCode() != null ? req.statusCode() : 200;
    MockGeneratorService.MockResponse result =
        generator.generate(req.oasDocument(), req.method(), req.path(), status);

    return ResponseEntity.status(result.status())
        .contentType(MediaType.parseMediaType(result.contentType()))
        .header("X-Mock-Response", "true")
        .body(result.body());
  }

  public record InlineRequest(
      Map<String, Object> oasDocument, String path, String method, Integer statusCode) {}
}
