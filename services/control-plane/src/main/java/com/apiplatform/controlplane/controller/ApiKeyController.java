package com.apiplatform.controlplane.controller;

import com.apiplatform.controlplane.dto.ApiKeyDto;
import com.apiplatform.controlplane.dto.PageDto;
import com.apiplatform.controlplane.exception.AppException;
import com.apiplatform.controlplane.security.ApiPrincipal;
import com.apiplatform.controlplane.service.ApiKeyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @Value("${app.internal-token}")
    private String internalToken;

    @GetMapping
    public PageDto<ApiKeyDto.Summary> list(
            @AuthenticationPrincipal ApiPrincipal principal,
            @RequestParam(required = false) String proxyId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return apiKeyService.list(principal.tenantId(), proxyId, status, page, limit);
    }

    @GetMapping("/{id}")
    public ApiKeyDto.Summary get(@AuthenticationPrincipal ApiPrincipal principal, @PathVariable String id) {
        return apiKeyService.get(principal.tenantId(), id);
    }

    @PostMapping
    public ResponseEntity<ApiKeyDto.CreateResponse> create(
            @AuthenticationPrincipal ApiPrincipal principal,
            @Valid @RequestBody ApiKeyDto.CreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(apiKeyService.create(principal.tenantId(), principal.userId(), req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(@AuthenticationPrincipal ApiPrincipal principal, @PathVariable String id) {
        apiKeyService.revoke(principal.tenantId(), id);
        return ResponseEntity.noContent().build();
    }

    // Internal: called by gateway to validate a raw API key
    @PostMapping("/_internal/validate")
    public ApiKeyDto.ValidationResult validate(
            @RequestHeader("X-Internal-Token") String token,
            @RequestBody ApiKeyDto.ValidateRequest req) {
        if (!internalToken.equals(token)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        return apiKeyService.validate(req.key(), req.proxyId());
    }
}
