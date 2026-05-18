package com.apiplatform.controlplane.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import java.net.URI;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AiProxyGeneratorService {

  @Value("${app.openai-api-key:}")
  private String openAiApiKey;

  private final ObjectMapper objectMapper;

  public AiProxyGeneratorService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public record GeneratedProxyConfig(
      String targetUrl,
      String pathPrefix,
      boolean stripPrefix,
      String authType,
      int rateLimitRpm,
      Map<String, String> forwardHeaders,
      String rationale) {}

  private static final String SYSTEM_PROMPT =
      """
      You are an API gateway configuration expert. Analyze the OpenAPI Specification and return ONLY \
      a JSON object (no markdown fences, no explanation) with this exact structure:
      {
        "targetUrl": "<scheme+host+port from servers[0], no path, no trailing slash>",
        "pathPrefix": "<gateway routing prefix starting with /, e.g. /v1/petstore>",
        "stripPrefix": true,
        "authType": "<api_key|jwt|none>",
        "rateLimitRpm": <integer>,
        "forwardHeaders": {},
        "rationale": "<1-2 sentences explaining the choices>"
      }

      Rules:
      - targetUrl: take servers[0].url, keep scheme+host+port only, strip any path component
      - pathPrefix: derive from the API title slug or basePath; must start with /
      - stripPrefix: true (the prefix is added by the gateway, not the backend)
      - authType: apiKey scheme → api_key; http bearer/oauth2 → jwt; nothing → none
      - rateLimitRpm: 60 for public APIs, 600 for internal, 100 if unknown
      - forwardHeaders: only meaningful headers (e.g. X-Api-Version); otherwise empty {}
      """;

  public GeneratedProxyConfig generate(
      String oasContent, OasValidatorService.ParsedOas parsedOas, List<String> warnings) {
    if (openAiApiKey == null || openAiApiKey.isBlank()) {
      log.info("OPENAI_API_KEY not set — using rule-based proxy config derivation");
      warnings.add(
          "AI proxy generation unavailable (OPENAI_API_KEY not set); used rule-based defaults");
      return ruleBasedFallback(parsedOas);
    }

    try {
      OpenAIClient client = OpenAIOkHttpClient.builder().apiKey(openAiApiKey).build();

      String truncated =
          oasContent.length() > 48_000
              ? oasContent.substring(0, 48_000) + "\n\n... (truncated)"
              : oasContent;

      ChatCompletion completion =
          client
              .chat()
              .completions()
              .create(
                  ChatCompletionCreateParams.builder()
                      .model(ChatModel.GPT_4O)
                      .maxCompletionTokens(512)
                      .addSystemMessage(SYSTEM_PROMPT)
                      .addUserMessage(
                          "Generate gateway proxy configuration for this OAS:\n\n" + truncated)
                      .build());

      String raw =
          completion.choices().get(0).message().content().orElse("{}");
      return parseResponse(raw, parsedOas, warnings);

    } catch (Exception e) {
      log.warn("AI proxy generation failed: {}; falling back to rule-based", e.getMessage());
      warnings.add(
          "AI proxy generation failed (" + e.getMessage() + "); used rule-based defaults");
      return ruleBasedFallback(parsedOas);
    }
  }

  @SuppressWarnings("unchecked")
  private GeneratedProxyConfig parseResponse(
      String raw, OasValidatorService.ParsedOas parsedOas, List<String> warnings) {
    try {
      String json = raw.trim();
      if (json.startsWith("```")) {
        json = json.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("```$", "").trim();
      }

      Map<String, Object> parsed = objectMapper.readValue(json, Map.class);

      String targetUrl =
          (String) parsed.getOrDefault("targetUrl", fallbackTargetUrl(parsedOas));
      String pathPrefix =
          (String) parsed.getOrDefault("pathPrefix", "/" + safeName(parsedOas.title()));
      boolean stripPrefix = Boolean.TRUE.equals(parsed.getOrDefault("stripPrefix", true));
      String authType = (String) parsed.getOrDefault("authType", "none");
      int rateLimitRpm =
          parsed.get("rateLimitRpm") instanceof Number n ? n.intValue() : 60;
      Map<String, String> fwd =
          parsed.get("forwardHeaders") instanceof Map m ? (Map<String, String>) m : Map.of();
      String rationale =
          (String) parsed.getOrDefault("rationale", "AI-generated configuration");

      return new GeneratedProxyConfig(
          targetUrl, pathPrefix, stripPrefix, authType, rateLimitRpm, fwd, rationale);

    } catch (Exception e) {
      warnings.add("Could not parse AI response; used rule-based defaults");
      return ruleBasedFallback(parsedOas);
    }
  }

  private GeneratedProxyConfig ruleBasedFallback(OasValidatorService.ParsedOas parsedOas) {
    String targetUrl = fallbackTargetUrl(parsedOas);
    String pathPrefix = "/" + safeName(parsedOas.title());

    String authType = "none";
    if (!parsedOas.securitySchemes().isEmpty()) {
      String schemes = parsedOas.securitySchemes().toString().toLowerCase();
      if (schemes.contains("apikey")) authType = "api_key";
      else if (schemes.contains("bearer") || schemes.contains("oauth")) authType = "jwt";
    }

    return new GeneratedProxyConfig(
        targetUrl,
        pathPrefix,
        true,
        authType,
        60,
        Map.of(),
        "Rule-based configuration derived from OAS servers and security schemes");
  }

  private String fallbackTargetUrl(OasValidatorService.ParsedOas parsedOas) {
    if (parsedOas.servers().isEmpty()) return "https://your-backend.example.com";
    String url = (String) parsedOas.servers().get(0).get("url");
    try {
      URI uri = new URI(url);
      return uri.getScheme()
          + "://"
          + uri.getHost()
          + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
    } catch (Exception e) {
      return url;
    }
  }

  private String safeName(String title) {
    return title
        .toLowerCase()
        .replaceAll("[^a-z0-9]", "-")
        .replaceAll("-{2,}", "-")
        .replaceAll("^-|-$", "");
  }
}
