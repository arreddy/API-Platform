package com.apiplatform.gateway.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "API Platform — Gateway",
            version = "1.0.0",
            description =
                "Runtime gateway that routes, authenticates, and rate-limits traffic to upstream APIs. "
                    + "Routes are loaded dynamically from the control plane — no static endpoints are defined here. "
                    + "Use the control plane UI (port 3001) to manage proxies."),
    servers = {@Server(url = "http://localhost:3000", description = "Local dev")})
public class OpenApiConfig {}
