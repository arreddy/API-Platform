package com.apiplatform.controlplane.controller;

import com.apiplatform.controlplane.dto.DeployDto;
import com.apiplatform.controlplane.exception.AppException;
import com.apiplatform.controlplane.security.ApiPrincipal;
import com.apiplatform.controlplane.service.DeploymentOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Deploy", description = "One-shot OAS → AI proxy config → gateway deploy")
@RestController
@RequestMapping("/api/v1/deploy")
@RequiredArgsConstructor
public class DeployController {

  private final DeploymentOrchestrator orchestrator;

  @Operation(
      summary = "Deploy from OAS",
      description =
          "Registers the OAS, uses OpenAI to generate the optimal proxy configuration, "
              + "creates the proxy, and triggers an immediate gateway route reload — all in one call. "
              + "Supply the OAS as a multipart `oas` file **or** the `oasText` param. "
              + "Query params `targetUrlOverride`, `pathPrefixOverride`, `authTypeOverride`, "
              + "and `rateLimitRpmOverride` let you nudge or replace the AI's choices.")
  @PostMapping(consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_JSON_VALUE})
  public ResponseEntity<DeployDto.Response> deploy(
      @AuthenticationPrincipal ApiPrincipal principal,
      @RequestPart(value = "oas", required = false) MultipartFile oasFile,
      @RequestParam(required = false) String oasText,
      @RequestParam(required = false) String targetUrlOverride,
      @RequestParam(required = false) String pathPrefixOverride,
      @RequestParam(required = false) String authTypeOverride,
      @RequestParam(required = false) Integer rateLimitRpmOverride)
      throws IOException {

    String content = resolveContent(oasFile, oasText);

    DeployDto.Request overrides =
        new DeployDto.Request(
            null, targetUrlOverride, pathPrefixOverride, authTypeOverride, rateLimitRpmOverride);

    DeployDto.Response resp =
        orchestrator.deploy(principal.tenantId(), principal.userId(), content, overrides);

    return ResponseEntity.status(HttpStatus.CREATED).body(resp);
  }

  private String resolveContent(MultipartFile file, String text) throws IOException {
    if (file != null && !file.isEmpty()) {
      return new String(file.getBytes(), StandardCharsets.UTF_8);
    }
    if (text != null && !text.isBlank()) return text;
    throw new AppException(
        HttpStatus.BAD_REQUEST, "Provide an OAS file (multipart `oas`) or `oasText` parameter");
  }
}
