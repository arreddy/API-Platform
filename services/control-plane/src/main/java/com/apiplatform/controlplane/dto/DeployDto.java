package com.apiplatform.controlplane.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeployDto {

  public record Request(
      String oasText,
      String targetUrlOverride,
      String pathPrefixOverride,
      String authTypeOverride,
      Integer rateLimitRpmOverride) {}

  public record Response(
      ApiDto.Full api, ProxyDto.Full proxy, String aiRationale, List<String> warnings) {}
}
