package com.apiplatform.controlplane.controller;

import com.apiplatform.controlplane.dto.PageDto;
import com.apiplatform.controlplane.dto.ProxyDto;
import com.apiplatform.controlplane.entity.Proxy;
import com.apiplatform.controlplane.exception.AppException;
import com.apiplatform.controlplane.security.ApiPrincipal;
import com.apiplatform.controlplane.service.ProxyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/proxies")
@RequiredArgsConstructor
public class ProxyController {

    private final ProxyService proxyService;

    @Value("${app.internal-token}")
    private String internalToken;

    @GetMapping
    public PageDto<ProxyDto.Summary> list(
            @AuthenticationPrincipal ApiPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String apiId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return proxyService.list(principal.tenantId(), status, apiId, page, limit);
    }

    @GetMapping("/{id}")
    public ProxyDto.Full get(@AuthenticationPrincipal ApiPrincipal principal, @PathVariable String id) {
        return proxyService.get(principal.tenantId(), id);
    }

    @PostMapping
    public ResponseEntity<ProxyDto.Full> create(
            @AuthenticationPrincipal ApiPrincipal principal,
            @Valid @RequestBody ProxyDto.CreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(proxyService.create(principal.tenantId(), principal.userId(), req));
    }

    @PutMapping("/{id}")
    public ProxyDto.Full update(
            @AuthenticationPrincipal ApiPrincipal principal,
            @PathVariable String id,
            @RequestBody ProxyDto.UpdateRequest req) {
        return proxyService.update(principal.tenantId(), id, principal.userId(), req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal ApiPrincipal principal, @PathVariable String id) {
        proxyService.delete(principal.tenantId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/versions")
    public List<ProxyDto.VersionSummary> versions(
            @AuthenticationPrincipal ApiPrincipal principal, @PathVariable String id) {
        return proxyService.getVersions(principal.tenantId(), id);
    }

    @PostMapping("/{id}/rollback/{version}")
    public ProxyDto.Full rollback(
            @AuthenticationPrincipal ApiPrincipal principal,
            @PathVariable String id,
            @PathVariable int version) {
        return proxyService.rollback(principal.tenantId(), id, principal.userId(), version);
    }

    // Internal endpoint — used by gateway to load all active proxies
    @GetMapping("/_internal/active")
    public List<Proxy> activeProxies(@RequestHeader("X-Internal-Token") String token) {
        if (!internalToken.equals(token)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        return proxyService.getAllActive();
    }
}
