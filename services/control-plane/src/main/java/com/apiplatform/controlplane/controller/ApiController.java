package com.apiplatform.controlplane.controller;

import com.apiplatform.controlplane.dto.ApiDto;
import com.apiplatform.controlplane.dto.PageDto;
import com.apiplatform.controlplane.security.ApiPrincipal;
import com.apiplatform.controlplane.service.ApiRegistryService;
import com.apiplatform.controlplane.service.OasValidatorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/apis")
@RequiredArgsConstructor
public class ApiController {

    private final ApiRegistryService apiService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public PageDto<ApiDto.Summary> list(
            @AuthenticationPrincipal ApiPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return apiService.list(principal.tenantId(), status, search, page, limit);
    }

    @GetMapping("/{id}")
    public ApiDto.Full get(@AuthenticationPrincipal ApiPrincipal principal, @PathVariable String id) {
        return apiService.get(principal.tenantId(), id);
    }

    @GetMapping("/{id}/oas")
    public ResponseEntity<Object> getOas(
            @AuthenticationPrincipal ApiPrincipal principal,
            @PathVariable String id,
            @RequestParam(defaultValue = "json") String format) throws Exception {
        Map<String, Object> doc = apiService.getOasDocument(principal.tenantId(), id);
        if ("yaml".equals(format)) {
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            String yaml = yamlMapper.writeValueAsString(doc);
            return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/x-yaml")).body(yaml);
        }
        return ResponseEntity.ok(doc);
    }

    @PostMapping(consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<ApiDto.RegisterResponse> register(
            @AuthenticationPrincipal ApiPrincipal principal,
            @RequestPart(value = "oas", required = false) MultipartFile oasFile,
            @RequestParam(required = false) String oasText,
            @RequestParam(value = "name", required = false) String nameOverride) throws IOException {

        String content = resolveContent(oasFile, oasText);
        ApiDto.RegisterResponse resp = apiService.register(
                principal.tenantId(), principal.userId(), content, nameOverride);
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @PutMapping("/{id}")
    public ApiDto.Full update(
            @AuthenticationPrincipal ApiPrincipal principal,
            @PathVariable String id,
            @RequestBody ApiDto.UpdateRequest req) {
        return apiService.update(principal.tenantId(), id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal ApiPrincipal principal, @PathVariable String id) {
        apiService.delete(principal.tenantId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/validate", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public Map<String, Object> validate(
            @RequestPart(value = "oas", required = false) MultipartFile oasFile,
            @RequestParam(required = false) String oasText) throws IOException {
        OasValidatorService.ParsedOas parsed = apiService.validate(resolveContent(oasFile, oasText));
        return Map.of("valid", true, "summary", Map.of(
                "title", parsed.title(),
                "version", parsed.version(),
                "oasVersion", parsed.oasVersion(),
                "endpointCount", parsed.endpoints().size(),
                "servers", parsed.servers(),
                "tags", parsed.tags(),
                "securitySchemes", parsed.securitySchemes().keySet()));
    }

    private String resolveContent(MultipartFile file, String text) throws IOException {
        if (file != null && !file.isEmpty()) {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        }
        if (text != null && !text.isBlank()) return text;
        throw new com.apiplatform.controlplane.exception.AppException(
                HttpStatus.BAD_REQUEST, "Provide an OAS file (multipart) or oas parameter");
    }
}
