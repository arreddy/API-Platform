package com.apiplatform.controlplane.service;

import com.apiplatform.controlplane.dto.ApiKeyDto;
import com.apiplatform.controlplane.dto.PageDto;
import com.apiplatform.controlplane.entity.ApiKey;
import com.apiplatform.controlplane.exception.AppException;
import com.apiplatform.controlplane.repository.ApiKeyRepository;
import com.apiplatform.controlplane.repository.ProxyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final ProxyRepository proxyRepository;
    private final BCryptPasswordEncoder bcrypt;
    private final SecureRandom secureRandom = new SecureRandom();

    public PageDto<ApiKeyDto.Summary> list(String tenantId, String proxyId, String status, int page, int size) {
        PageRequest pageable = PageRequest.of(page - 1, size, Sort.by("createdAt").descending());
        var result = (proxyId != null)
                ? apiKeyRepository.findByTenantIdAndProxyId(tenantId, proxyId, pageable)
                : (status != null)
                ? apiKeyRepository.findByTenantIdAndStatus(tenantId, status, pageable)
                : apiKeyRepository.findByTenantId(tenantId, pageable);
        return PageDto.of(result.map(ApiKeyDto.Summary::from));
    }

    public ApiKeyDto.Summary get(String tenantId, String id) {
        return ApiKeyDto.Summary.from(apiKeyRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "API key not found")));
    }

    @Transactional
    public ApiKeyDto.CreateResponse create(String tenantId, String userId, ApiKeyDto.CreateRequest req) {
        if (req.proxyId() != null) {
            proxyRepository.findByTenantIdAndId(tenantId, req.proxyId())
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Proxy not found"));
        }

        // Generate raw key: apk_ + 48 hex chars
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        String rawKey = "apk_" + HexFormat.of().formatHex(bytes);
        String prefix = rawKey.substring(0, 12);
        String hash = bcrypt.encode(rawKey);

        ApiKey key = ApiKey.builder()
                .tenantId(tenantId)
                .proxyId(req.proxyId())
                .name(req.name())
                .keyPrefix(prefix)
                .keyHash(hash)
                .scopes(req.scopes() != null ? req.scopes().toArray(new String[0]) : new String[0])
                .rateLimit(req.rateLimit() != null ? req.rateLimit() : 1000)
                .rateLimitWindow(req.rateLimitWindow() != null ? req.rateLimitWindow() : "1h")
                .expiresAt(req.expiresAt() != null ? OffsetDateTime.parse(req.expiresAt()) : null)
                .status("active")
                .createdBy(userId)
                .build();

        ApiKey saved = apiKeyRepository.save(key);
        return new ApiKeyDto.CreateResponse(ApiKeyDto.Summary.from(saved), rawKey);
    }

    @Transactional
    public void revoke(String tenantId, String id) {
        ApiKey key = apiKeyRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "API key not found"));
        key.setStatus("revoked");
        apiKeyRepository.save(key);
    }

    // Called by gateway — validates raw key and returns metadata
    @Transactional
    public ApiKeyDto.ValidationResult validate(String rawKey, String proxyId) {
        if (rawKey == null || rawKey.length() < 12) {
            return ApiKeyDto.ValidationResult.invalid();
        }
        String prefix = rawKey.substring(0, 12);
        var candidates = apiKeyRepository.findByKeyPrefixAndStatus(prefix, "active");

        for (ApiKey candidate : candidates) {
            if (!bcrypt.matches(rawKey, candidate.getKeyHash())) continue;

            if (candidate.getExpiresAt() != null && candidate.getExpiresAt().isBefore(OffsetDateTime.now())) {
                candidate.setStatus("expired");
                apiKeyRepository.save(candidate);
                continue;
            }

            if (proxyId != null && candidate.getProxyId() != null && !candidate.getProxyId().equals(proxyId)) continue;

            // Non-blocking last_used_at update
            candidate.setLastUsedAt(OffsetDateTime.now());
            apiKeyRepository.save(candidate);

            return new ApiKeyDto.ValidationResult(true, candidate.getId(), candidate.getTenantId(),
                    candidate.getProxyId(), candidate.getRateLimit(), candidate.getRateLimitWindow());
        }
        return ApiKeyDto.ValidationResult.invalid();
    }
}
