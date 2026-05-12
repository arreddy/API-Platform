package com.apiplatform.controlplane.service;

import com.apiplatform.controlplane.dto.ApiDto;
import com.apiplatform.controlplane.dto.PageDto;
import com.apiplatform.controlplane.entity.Api;
import com.apiplatform.controlplane.exception.AppException;
import com.apiplatform.controlplane.repository.ApiRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApiRegistryService {

  private final ApiRepository apiRepository;
  private final OasValidatorService oasValidator;

  public PageDto<ApiDto.Summary> list(
      String tenantId, String status, String search, int page, int size) {
    PageRequest pageable = PageRequest.of(page - 1, size, Sort.by("createdAt").descending());
    Page<Api> result;
    if (search != null && !search.isBlank()) {
      result = apiRepository.searchByTitle(tenantId, search, pageable);
    } else if (status != null) {
      result = apiRepository.findByTenantIdAndStatus(tenantId, status, pageable);
    } else {
      result = apiRepository.findByTenantId(tenantId, pageable);
    }
    return PageDto.of(result.map(ApiDto.Summary::from));
  }

  public ApiDto.Full get(String tenantId, String id) {
    Api api =
        apiRepository
            .findByTenantIdAndId(tenantId, id)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "API not found"));
    return ApiDto.Full.from(api);
  }

  public Map<String, Object> getOasDocument(String tenantId, String id) {
    Api api =
        apiRepository
            .findByTenantIdAndId(tenantId, id)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "API not found"));
    return api.getOasDocument();
  }

  @Transactional
  public ApiDto.RegisterResponse register(
      String tenantId, String userId, String oasContent, String nameOverride) {
    OasValidatorService.ParsedOas parsed = oasValidator.parseAndValidate(oasContent);

    String name =
        nameOverride != null && !nameOverride.isBlank()
            ? nameOverride
            : parsed.title().toLowerCase().replaceAll("[^a-z0-9-]", "-").replaceAll("-{2,}", "-");

    if (apiRepository.existsByTenantIdAndName(tenantId, name)) {
      throw new AppException(
          HttpStatus.CONFLICT,
          "An API with name \"" + name + "\" already exists. Provide a different name.");
    }

    Api api =
        Api.builder()
            .tenantId(tenantId)
            .name(name)
            .title(parsed.title())
            .version(parsed.version())
            .description(parsed.description())
            .oasVersion(parsed.oasVersion())
            .oasDocument(parsed.rawDocument())
            .basePath(parsed.basePath())
            .servers(parsed.servers())
            .endpoints(parsed.endpoints())
            .securitySchemes(parsed.securitySchemes())
            .tags(parsed.tags().toArray(new String[0]))
            .status("active")
            .createdBy(userId)
            .build();

    Api saved = apiRepository.save(api);

    // Generate suggested proxy config from parsed endpoints
    List<Map<String, Object>> suggestedRoutes =
        parsed.endpoints().stream()
            .limit(10)
            .map(
                ep ->
                    Map.<String, Object>of(
                        "method", ep.get("method"),
                        "path", ep.get("path"),
                        "operationId", ep.getOrDefault("operationId", "")))
            .toList();

    String targetUrl =
        parsed.servers().isEmpty()
            ? "https://your-backend.example.com"
            : (String) parsed.servers().get(0).get("url");

    return new ApiDto.RegisterResponse(
        ApiDto.Summary.from(saved),
        Map.of("targetUrl", targetUrl, "pathPrefix", "/" + name, "routes", suggestedRoutes));
  }

  @Transactional
  public ApiDto.Full update(String tenantId, String id, ApiDto.UpdateRequest req) {
    Api api =
        apiRepository
            .findByTenantIdAndId(tenantId, id)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "API not found"));

    if (req.description() != null) api.setDescription(req.description());
    if (req.status() != null) api.setStatus(req.status());
    if (req.name() != null) api.setName(req.name());

    if (req.oasContent() != null) {
      OasValidatorService.ParsedOas parsed = oasValidator.parseAndValidate(req.oasContent());
      api.setTitle(parsed.title());
      api.setVersion(parsed.version());
      api.setOasVersion(parsed.oasVersion());
      api.setOasDocument(parsed.rawDocument());
      api.setBasePath(parsed.basePath());
      api.setServers(parsed.servers());
      api.setEndpoints(parsed.endpoints());
      api.setSecuritySchemes(parsed.securitySchemes());
      api.setTags(parsed.tags().toArray(new String[0]));
    }

    return ApiDto.Full.from(apiRepository.save(api));
  }

  @Transactional
  public void delete(String tenantId, String id) {
    Api api =
        apiRepository
            .findByTenantIdAndId(tenantId, id)
            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "API not found"));
    apiRepository.delete(api);
  }

  public OasValidatorService.ParsedOas validate(String oasContent) {
    return oasValidator.parseAndValidate(oasContent);
  }
}
