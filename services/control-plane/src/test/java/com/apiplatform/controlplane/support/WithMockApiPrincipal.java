package com.apiplatform.controlplane.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.test.context.support.WithSecurityContext;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithMockApiPrincipalFactory.class)
public @interface WithMockApiPrincipal {
  String userId() default "00000000-0000-0000-0000-000000000002";

  String tenantId() default "00000000-0000-0000-0000-000000000001";

  String role() default "admin";
}
