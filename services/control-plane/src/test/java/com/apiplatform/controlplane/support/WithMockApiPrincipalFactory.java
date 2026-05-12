package com.apiplatform.controlplane.support;

import com.apiplatform.controlplane.security.ApiPrincipal;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

public class WithMockApiPrincipalFactory
    implements WithSecurityContextFactory<WithMockApiPrincipal> {

  @Override
  public SecurityContext createSecurityContext(WithMockApiPrincipal annotation) {
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    ApiPrincipal principal =
        new ApiPrincipal(annotation.userId(), annotation.tenantId(), annotation.role());
    var auth =
        new UsernamePasswordAuthenticationToken(
            principal,
            null,
            List.of(new SimpleGrantedAuthority("ROLE_" + annotation.role().toUpperCase())));
    context.setAuthentication(auth);
    return context;
  }
}
