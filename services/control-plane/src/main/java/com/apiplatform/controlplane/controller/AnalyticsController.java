package com.apiplatform.controlplane.controller;

import com.apiplatform.controlplane.dto.AnalyticsDto;
import com.apiplatform.controlplane.dto.PageDto;
import com.apiplatform.controlplane.entity.RequestLog;
import com.apiplatform.controlplane.exception.AppException;
import com.apiplatform.controlplane.security.ApiPrincipal;
import com.apiplatform.controlplane.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @Value("${app.internal-token}")
    private String internalToken;

    @GetMapping("/summary")
    public AnalyticsDto.Summary summary(
            @AuthenticationPrincipal ApiPrincipal principal,
            @RequestParam(required = false) String proxyId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return analyticsService.getSummary(
                principal.tenantId(), proxyId,
                from != null ? OffsetDateTime.parse(from) : null,
                to != null ? OffsetDateTime.parse(to) : null);
    }

    @GetMapping("/requests")
    public PageDto<RequestLog> requests(
            @AuthenticationPrincipal ApiPrincipal principal,
            @RequestParam(required = false) String proxyId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int limit) {
        return analyticsService.getRequests(principal.tenantId(), proxyId, page, limit);
    }

    // Internal: batch ingest from gateway
    @PostMapping("/_internal/ingest")
    public ResponseEntity<Void> ingest(
            @RequestHeader("X-Internal-Token") String token,
            @RequestBody List<AnalyticsDto.LogEntry> entries) {
        if (!internalToken.equals(token)) throw new AppException(HttpStatus.FORBIDDEN, "Forbidden");
        if (entries.size() > 500) throw new AppException(HttpStatus.BAD_REQUEST, "Max 500 entries per batch");
        analyticsService.ingestBatch(entries);
        return ResponseEntity.accepted().build();
    }
}
