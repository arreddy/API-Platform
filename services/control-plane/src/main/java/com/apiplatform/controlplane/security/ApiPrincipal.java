package com.apiplatform.controlplane.security;

public record ApiPrincipal(String userId, String tenantId, String role) {}
