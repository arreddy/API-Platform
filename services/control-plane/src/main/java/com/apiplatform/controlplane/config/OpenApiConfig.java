package com.apiplatform.controlplane.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "API Platform — Control Plane",
            version = "1.0.0",
            description =
                "Manage APIs, proxies, API keys, and analytics. "
                    + "Authenticate via **POST /api/v1/auth/login** and paste the token below.",
            contact = @Contact(name = "API Platform")),
    servers = {@Server(url = "http://localhost:3001", description = "Local dev")},
    security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "JWT issued by POST /api/v1/auth/login")
public class OpenApiConfig {}
