package com.apiplatform.controlplane.service;

import com.apiplatform.controlplane.dto.ApiDto;
import com.apiplatform.controlplane.dto.DeployDto;
import com.apiplatform.controlplane.dto.ProxyDto;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeploymentOrchestrator {

  private final ApiRegistryService apiRegistryService;
  private final OasValidatorService oasValidator;
  private final AiProxyGeneratorService aiProxyGenerator;
  private final ProxyService proxyService;
  private final GatewayRefreshService gatewayRefreshService;

  public DeployDto.Response deploy(
      String tenantId, String userId, String oasContent, DeployDto.Request overrides) {

    List<String> warnings = new ArrayList<>();

    // 1. Register API — validates OAS, stores it, runs Spectral + AI analysis
    ApiDto.RegisterResponse regResp =
        apiRegistryService.register(tenantId, userId, oasContent, null);
    String apiId = regResp.api().id();

    // 2. Re-parse OAS (fast, in-memory) to hand structured metadata to the AI generator
    OasValidatorService.ParsedOas parsedOas = oasValidator.parseAndValidate(oasContent);

    // 3. Ask OpenAI to generate the proxy configuration
    AiProxyGeneratorService.GeneratedProxyConfig gen =
        aiProxyGenerator.generate(oasContent, parsedOas, warnings);

    // 4. Apply caller overrides on top of AI choices
    String targetUrl = coalesce(overrides != null ? overrides.targetUrlOverride() : null, gen.targetUrl());
    String pathPrefix = coalesce(overrides != null ? overrides.pathPrefixOverride() : null, gen.pathPrefix());
    String authType   = coalesce(overrides != null ? overrides.authTypeOverride()   : null, gen.authType());
    int rateLimitRpm  = (overrides != null && overrides.rateLimitRpmOverride() != null)
        ? overrides.rateLimitRpmOverride()
        : gen.rateLimitRpm();

    // 5. Build policies map matching the gateway's ProxyConfig expectations
    Map<String, Object> policies = new LinkedHashMap<>();
    policies.put("auth", Map.of("type", authType));
    if (rateLimitRpm > 0) {
      policies.put("rateLimit", Map.of("enabled", true, "requests", rateLimitRpm, "window", "1m"));
    }

    // 5a. Derive explicit routes from every parsed OAS endpoint
    List<Map<String, Object>> routes =
        parsedOas.endpoints().stream()
            .map(
                ep -> {
                  Map<String, Object> route = new LinkedHashMap<>();
                  route.put("method", ep.get("method"));
                  route.put("path", ep.get("path"));
                  if (ep.get("operationId") != null) route.put("operationId", ep.get("operationId"));
                  if (ep.get("summary") != null) route.put("summary", ep.get("summary"));
                  if (ep.get("tags") != null) route.put("tags", ep.get("tags"));
                  return route;
                })
            .toList();

    ProxyDto.CreateRequest createReq =
        new ProxyDto.CreateRequest(
            parsedOas.title() + " Proxy",
            "Auto-deployed from OAS: " + parsedOas.title() + " v" + parsedOas.version(),
            targetUrl,
            pathPrefix,
            true,
            apiId,
            policies,
            routes,
            new LinkedHashMap<>(gen.forwardHeaders()));

    // 6. Persist the proxy (fires its own transaction)
    ProxyDto.Full proxy = proxyService.create(tenantId, userId, createReq);

    // 7. Tell the gateway to reload routes immediately instead of waiting up to 30s
    gatewayRefreshService.triggerRefresh();

    ApiDto.Full api = apiRegistryService.get(tenantId, apiId);
    return new DeployDto.Response(api, proxy, gen.rationale(), warnings.isEmpty() ? null : warnings);
  }

  private String coalesce(String override, String fallback) {
    return (override != null && !override.isBlank()) ? override : fallback;
  }
}
