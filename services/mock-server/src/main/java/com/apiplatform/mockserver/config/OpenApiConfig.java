package com.apiplatform.mockserver.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "API Platform — Mock Server",
            version = "1.0.0",
            description =
                "Generates realistic mock responses from OAS 3.x documents. "
                    + "Use `/mock/{proxyId}/**` to mock a registered proxy, "
                    + "or `/mock/inline` to supply an OAS document directly."),
    servers = {@Server(url = "http://localhost:3002", description = "Local dev")})
public class OpenApiConfig {}
