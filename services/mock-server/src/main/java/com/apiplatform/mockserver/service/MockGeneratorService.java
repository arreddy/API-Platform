package com.apiplatform.mockserver.service;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import net.datafaker.Faker;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class MockGeneratorService {

    private final Faker faker = new Faker();

    public record MockResponse(int status, Object body, String contentType) {}

    public MockResponse generate(Map<String, Object> oasDoc, String method, String path, int statusCode) {
        // Parse OAS from map
        OpenAPI openAPI = parseOas(oasDoc);
        if (openAPI == null) {
            return new MockResponse(404, Map.of("error", "Cannot parse OAS document"), "application/json");
        }

        PathItem pathItem = openAPI.getPaths() != null ? openAPI.getPaths().get(path) : null;
        if (pathItem == null) {
            return new MockResponse(404, Map.of("error", "Path not found: " + path), "application/json");
        }

        Operation operation = getOperation(pathItem, method);
        if (operation == null) {
            return new MockResponse(405, Map.of("error", "Method not allowed"), "application/json");
        }

        if (operation.getResponses() == null) {
            return new MockResponse(statusCode, Map.of(), "application/json");
        }

        // Find best matching response
        ApiResponse apiResponse = operation.getResponses().get(String.valueOf(statusCode));
        if (apiResponse == null) apiResponse = operation.getResponses().get("2XX");
        if (apiResponse == null) apiResponse = operation.getResponses().get("default");
        if (apiResponse == null && !operation.getResponses().isEmpty()) {
            apiResponse = operation.getResponses().values().iterator().next();
        }

        if (apiResponse == null || apiResponse.getContent() == null) {
            return new MockResponse(statusCode, Map.of(), "application/json");
        }

        var jsonContent = apiResponse.getContent().get("application/json");
        if (jsonContent == null) {
            String firstKey = apiResponse.getContent().keySet().iterator().next();
            return new MockResponse(statusCode, apiResponse.getDescription(), firstKey);
        }

        // Check for explicit examples
        if (jsonContent.getExamples() != null && !jsonContent.getExamples().isEmpty()) {
            Object example = jsonContent.getExamples().values().iterator().next().getValue();
            return new MockResponse(statusCode, example, "application/json");
        }
        if (jsonContent.getExample() != null) {
            return new MockResponse(statusCode, jsonContent.getExample(), "application/json");
        }

        // Generate from schema
        if (jsonContent.getSchema() != null) {
            Object generated = generateFromSchema(jsonContent.getSchema(), 0);
            return new MockResponse(statusCode, generated, "application/json");
        }

        return new MockResponse(statusCode, Map.of(), "application/json");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object generateFromSchema(Schema<?> schema, int depth) {
        if (depth > 5 || schema == null) return null;

        if (schema.getExample() != null) return schema.getExample();
        if (schema.getEnum() != null && !schema.getEnum().isEmpty()) return schema.getEnum().get(0);

        // allOf / anyOf / oneOf
        if (schema.getAllOf() != null && !schema.getAllOf().isEmpty()) {
            Map<String, Object> merged = new LinkedHashMap<>();
            for (Schema s : schema.getAllOf()) {
                Object part = generateFromSchema(s, depth);
                if (part instanceof Map m) merged.putAll(m);
            }
            return merged;
        }
        if (schema.getAnyOf() != null && !schema.getAnyOf().isEmpty())
            return generateFromSchema(schema.getAnyOf().get(0), depth + 1);
        if (schema.getOneOf() != null && !schema.getOneOf().isEmpty())
            return generateFromSchema(schema.getOneOf().get(0), depth + 1);

        String type = schema.getType();

        return switch (type != null ? type : "string") {
            case "object" -> generateObject(schema, depth);
            case "array" -> generateArray(schema, depth);
            case "integer" -> generateInteger(schema);
            case "number" -> generateNumber(schema);
            case "boolean" -> faker.bool().bool();
            case "null" -> null;
            default -> generateString(schema);
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> generateObject(Schema<?> schema, int depth) {
        Map<String, Object> obj = new LinkedHashMap<>();
        if (schema.getProperties() != null) {
            schema.getProperties().forEach((key, propSchema) ->
                    obj.put(key, generateFromSchema((Schema<?>) propSchema, depth + 1)));
        }
        return obj;
    }

    @SuppressWarnings("unchecked")
    private List<Object> generateArray(Schema<?> schema, int depth) {
        Schema<?> items = schema.getItems();
        Object item = items != null ? generateFromSchema(items, depth + 1) : faker.lorem().word();
        return depth == 0
                ? List.of(item, generateFromSchema(items, depth + 1))
                : List.of(item);
    }

    private long generateInteger(Schema<?> schema) {
        long min = schema.getMinimum() != null ? schema.getMinimum().longValue() : 0L;
        long max = schema.getMaximum() != null ? schema.getMaximum().longValue() : 1000L;
        return faker.number().numberBetween(min, max);
    }

    private double generateNumber(Schema<?> schema) {
        double min = schema.getMinimum() != null ? schema.getMinimum().doubleValue() : 0.0;
        double max = schema.getMaximum() != null ? schema.getMaximum().doubleValue() : 1000.0;
        return Math.round(faker.number().randomDouble(2, (long) min, (long) max) * 100.0) / 100.0;
    }

    private String generateString(Schema<?> schema) {
        String format = schema.getFormat();
        if (format == null) format = "";
        return switch (format) {
            case "date-time" -> faker.date().birthday().toInstant().toString();
            case "date" -> faker.date().birthday().toLocalDate().toString();
            case "email" -> faker.internet().emailAddress();
            case "uri", "url" -> faker.internet().url();
            case "uuid" -> UUID.randomUUID().toString();
            case "hostname" -> faker.internet().domainName();
            case "ipv4" -> faker.internet().ipV4Address();
            case "ipv6" -> faker.internet().ipV6Address();
            case "password" -> faker.internet().password();
            case "byte" -> Base64.getEncoder().encodeToString(faker.lorem().word().getBytes());
            default -> {
                int min = schema.getMinLength() != null ? schema.getMinLength() : 5;
                int max = schema.getMaxLength() != null ? schema.getMaxLength() : 30;
                yield faker.lorem().characters(min, max);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private OpenAPI parseOas(Map<String, Object> oasDoc) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String json = mapper.writeValueAsString(oasDoc);
            ParseOptions opts = new ParseOptions();
            opts.setResolve(false);
            var result = new OpenAPIV3Parser().readContents(json, null, opts);
            return result.getOpenAPI();
        } catch (Exception e) {
            return null;
        }
    }

    private Operation getOperation(PathItem item, String method) {
        return switch (method.toUpperCase()) {
            case "GET" -> item.getGet();
            case "POST" -> item.getPost();
            case "PUT" -> item.getPut();
            case "PATCH" -> item.getPatch();
            case "DELETE" -> item.getDelete();
            case "OPTIONS" -> item.getOptions();
            case "HEAD" -> item.getHead();
            default -> null;
        };
    }
}
