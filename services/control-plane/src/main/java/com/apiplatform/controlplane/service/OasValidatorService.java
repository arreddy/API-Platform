package com.apiplatform.controlplane.service;

import com.apiplatform.controlplane.exception.AppException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class OasValidatorService {

    private final ObjectMapper objectMapper;

    public record ParsedOas(
            String oasVersion,
            String title,
            String version,
            String description,
            String basePath,
            List<Map<String, Object>> servers,
            List<Map<String, Object>> endpoints,
            Map<String, Object> securitySchemes,
            List<String> tags,
            Map<String, Object> rawDocument
    ) {}

    public ParsedOas parseAndValidate(String content) {
        // Detect format and normalize to JSON string for the parser
        String normalizedContent = normalizeToJson(content);

        ParseOptions opts = new ParseOptions();
        opts.setResolve(false);

        SwaggerParseResult result = new OpenAPIV3Parser().readContents(normalizedContent, null, opts);

        if (result.getOpenAPI() == null) {
            String errors = result.getMessages() != null ? String.join("; ", result.getMessages()) : "unknown";
            throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "OAS validation failed: " + errors);
        }

        OpenAPI openAPI = result.getOpenAPI();

        // Re-parse raw document for storage
        Map<String, Object> rawDoc = parseToMap(content);

        String oasVersion = "3.0";
        if (openAPI.getSpecVersion() != null) {
            String n = openAPI.getSpecVersion().name(); // "V30" or "V31"
            oasVersion = n.charAt(1) + "." + n.substring(2);
        }

        String title = openAPI.getInfo() != null ? openAPI.getInfo().getTitle() : "Untitled";
        String version = openAPI.getInfo() != null ? openAPI.getInfo().getVersion() : "1.0.0";
        String description = openAPI.getInfo() != null ? openAPI.getInfo().getDescription() : null;

        // Servers
        List<Map<String, Object>> servers = new ArrayList<>();
        String basePath = null;
        if (openAPI.getServers() != null) {
            for (var s : openAPI.getServers()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("url", s.getUrl());
                if (s.getDescription() != null) m.put("description", s.getDescription());
                servers.add(m);
            }
            if (!servers.isEmpty()) {
                String url = (String) servers.get(0).get("url");
                try {
                    var uri = new java.net.URI(url);
                    if (uri.getPath() != null && !uri.getPath().isEmpty() && !uri.getPath().equals("/")) {
                        basePath = uri.getPath();
                    }
                } catch (Exception ignored) {
                    basePath = url;
                }
            }
        }

        // Endpoints + tags
        List<Map<String, Object>> endpoints = new ArrayList<>();
        Set<String> tagSet = new LinkedHashSet<>();

        if (openAPI.getPaths() != null) {
            openAPI.getPaths().forEach((path, pathItem) -> {
                Map<String, Operation> ops = getOperations(pathItem);
                ops.forEach((method, op) -> {
                    Map<String, Object> ep = new LinkedHashMap<>();
                    ep.put("path", path);
                    ep.put("method", method.toUpperCase());
                    if (op.getOperationId() != null) ep.put("operationId", op.getOperationId());
                    if (op.getSummary() != null) ep.put("summary", op.getSummary());
                    if (op.getTags() != null) {
                        ep.put("tags", op.getTags());
                        tagSet.addAll(op.getTags());
                    }
                    endpoints.add(ep);
                });
            });
        }

        // Security schemes
        Map<String, Object> securitySchemes = new LinkedHashMap<>();
        if (openAPI.getComponents() != null && openAPI.getComponents().getSecuritySchemes() != null) {
            openAPI.getComponents().getSecuritySchemes().forEach((k, v) ->
                    securitySchemes.put(k, Map.of("type", v.getType() != null ? v.getType().toString() : "unknown")));
        }

        return new ParsedOas(oasVersion, title, version, description, basePath,
                servers, endpoints, securitySchemes, new ArrayList<>(tagSet), rawDoc);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseToMap(String content) {
        content = content.trim();
        try {
            if (content.startsWith("{") || content.startsWith("[")) {
                return objectMapper.readValue(content, Map.class);
            }
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            return yamlMapper.readValue(content, Map.class);
        } catch (Exception e) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Cannot parse content as JSON or YAML");
        }
    }

    private String normalizeToJson(String content) {
        content = content.trim();
        if (content.startsWith("{") || content.startsWith("[")) return content;
        try {
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            Object obj = yamlMapper.readValue(content, Object.class);
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Cannot parse content as YAML");
        }
    }

    private Map<String, Operation> getOperations(PathItem item) {
        Map<String, Operation> ops = new LinkedHashMap<>();
        if (item.getGet() != null) ops.put("GET", item.getGet());
        if (item.getPost() != null) ops.put("POST", item.getPost());
        if (item.getPut() != null) ops.put("PUT", item.getPut());
        if (item.getPatch() != null) ops.put("PATCH", item.getPatch());
        if (item.getDelete() != null) ops.put("DELETE", item.getDelete());
        if (item.getOptions() != null) ops.put("OPTIONS", item.getOptions());
        if (item.getHead() != null) ops.put("HEAD", item.getHead());
        return ops;
    }
}
