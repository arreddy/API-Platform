package com.apiplatform.controlplane.service;

import com.apiplatform.controlplane.dto.PageDto;
import com.apiplatform.controlplane.dto.ProxyDto;
import com.apiplatform.controlplane.entity.Proxy;
import com.apiplatform.controlplane.entity.ProxyVersion;
import com.apiplatform.controlplane.exception.AppException;
import com.apiplatform.controlplane.repository.ApiRepository;
import com.apiplatform.controlplane.repository.ProxyRepository;
import com.apiplatform.controlplane.repository.ProxyVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProxyService {

    private final ProxyRepository proxyRepository;
    private final ProxyVersionRepository versionRepository;
    private final ApiRepository apiRepository;
    private final ObjectMapper objectMapper;

    public PageDto<ProxyDto.Summary> list(String tenantId, String status, String apiId, int page, int size) {
        PageRequest pageable = PageRequest.of(page - 1, size, Sort.by("createdAt").descending());
        var result = (status != null)
                ? proxyRepository.findByTenantIdAndStatus(tenantId, status, pageable)
                : (apiId != null)
                ? proxyRepository.findByTenantIdAndApiId(tenantId, apiId, pageable)
                : proxyRepository.findByTenantId(tenantId, pageable);
        return PageDto.of(result.map(ProxyDto.Summary::from));
    }

    public ProxyDto.Full get(String tenantId, String id) {
        return ProxyDto.Full.from(findOrThrow(tenantId, id));
    }

    public List<Proxy> getAllActive() {
        return proxyRepository.findByStatus("active");
    }

    @Transactional
    public ProxyDto.Full create(String tenantId, String userId, ProxyDto.CreateRequest req) {
        if (req.apiId() != null) {
            apiRepository.findByTenantIdAndId(tenantId, req.apiId())
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "API not found"));
        }

        if (proxyRepository.existsByTenantIdAndPathPrefixAndStatus(tenantId, req.pathPrefix(), "active")) {
            throw new AppException(HttpStatus.CONFLICT,
                    "Path prefix \"" + req.pathPrefix() + "\" is already in use");
        }

        Proxy proxy = Proxy.builder()
                .tenantId(tenantId)
                .apiId(req.apiId())
                .name(req.name())
                .description(req.description())
                .targetUrl(req.targetUrl())
                .pathPrefix(req.pathPrefix())
                .stripPrefix(req.stripPrefix() != null ? req.stripPrefix() : true)
                .version(1)
                .policies(req.policies() != null ? req.policies() : Map.of())
                .routes(req.routes() != null ? req.routes() : List.of())
                .headers(req.headers() != null ? req.headers() : Map.of())
                .status("active")
                .createdBy(userId)
                .build();

        Proxy saved = proxyRepository.save(proxy);
        saveVersion(saved, userId, "Initial version");
        return ProxyDto.Full.from(saved);
    }

    @Transactional
    public ProxyDto.Full update(String tenantId, String id, String userId, ProxyDto.UpdateRequest req) {
        Proxy proxy = findOrThrow(tenantId, id);

        if (req.pathPrefix() != null && !req.pathPrefix().equals(proxy.getPathPrefix())) {
            if (proxyRepository.existsByTenantIdAndPathPrefixAndStatusAndIdNot(
                    tenantId, req.pathPrefix(), "active", id)) {
                throw new AppException(HttpStatus.CONFLICT,
                        "Path prefix \"" + req.pathPrefix() + "\" is already in use");
            }
            proxy.setPathPrefix(req.pathPrefix());
        }

        if (req.name() != null) proxy.setName(req.name());
        if (req.description() != null) proxy.setDescription(req.description());
        if (req.targetUrl() != null) proxy.setTargetUrl(req.targetUrl());
        if (req.stripPrefix() != null) proxy.setStripPrefix(req.stripPrefix());
        if (req.apiId() != null) proxy.setApiId(req.apiId());
        if (req.policies() != null) proxy.setPolicies(req.policies());
        if (req.routes() != null) proxy.setRoutes(req.routes());
        if (req.headers() != null) proxy.setHeaders(req.headers());

        proxy.setVersion(proxy.getVersion() + 1);
        Proxy saved = proxyRepository.save(proxy);
        saveVersion(saved, userId, req.changeNote() != null ? req.changeNote() : "Updated to v" + saved.getVersion());
        return ProxyDto.Full.from(saved);
    }

    @Transactional
    public void delete(String tenantId, String id) {
        Proxy proxy = findOrThrow(tenantId, id);
        proxy.setStatus("inactive");
        proxyRepository.save(proxy);
    }

    public List<ProxyDto.VersionSummary> getVersions(String tenantId, String id) {
        findOrThrow(tenantId, id); // auth check
        return versionRepository.findByProxyIdOrderByVersionDesc(id)
                .stream().map(ProxyDto.VersionSummary::from).toList();
    }

    @Transactional
    public ProxyDto.Full rollback(String tenantId, String id, String userId, int targetVersion) {
        Proxy proxy = findOrThrow(tenantId, id);

        ProxyVersion pv = versionRepository.findByProxyIdAndVersion(id, targetVersion)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Version not found"));

        @SuppressWarnings("unchecked")
        Map<String, Object> snap = (Map<String, Object>) pv.getSnapshot();

        proxy.setTargetUrl((String) snap.getOrDefault("targetUrl", snap.get("target_url")));
        proxy.setPathPrefix((String) snap.getOrDefault("pathPrefix", snap.get("path_prefix")));
        proxy.setStripPrefix(Boolean.TRUE.equals(snap.getOrDefault("stripPrefix", snap.get("strip_prefix"))));
        proxy.setPolicies(toMap(snap.getOrDefault("policies", Map.of())));
        proxy.setRoutes(toList(snap.getOrDefault("routes", List.of())));
        proxy.setHeaders(toStringMap(snap.getOrDefault("headers", Map.of())));
        proxy.setVersion(proxy.getVersion() + 1);

        Proxy saved = proxyRepository.save(proxy);
        saveVersion(saved, userId, "Rolled back to v" + targetVersion);
        return ProxyDto.Full.from(saved);
    }

    private Proxy findOrThrow(String tenantId, String id) {
        return proxyRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Proxy not found"));
    }

    private void saveVersion(Proxy proxy, String userId, String note) {
        versionRepository.save(ProxyVersion.builder()
                .proxyId(proxy.getId())
                .version(proxy.getVersion())
                .snapshot(objectMapper.convertValue(proxy, Map.class))
                .changedBy(userId)
                .changeNote(note)
                .build());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object o) {
        return (o instanceof Map) ? (Map<String, Object>) o : Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toList(Object o) {
        return (o instanceof List) ? (List<Map<String, Object>>) o : List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> toStringMap(Object o) {
        return (o instanceof Map) ? (Map<String, String>) o : Map.of();
    }
}
