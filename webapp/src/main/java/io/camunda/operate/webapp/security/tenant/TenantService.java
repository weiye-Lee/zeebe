/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security.tenant;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;

@Component
public class TenantService {

  private static final Logger LOGGER = LoggerFactory.getLogger(TenantService.class);

  @Autowired
  private OperateProperties operateProperties;

  public AuthenticatedTenants getAuthenticatedTenants() {
    if (!isMultiTenancyEnabled()) {
      // disabled means no tenant check necessary.
      // thus, the user/app has access to all tenants.
      return AuthenticatedTenants.allTenants();
    }

    final var authentication = getCurrentTenantAwareAuthentication();
    final var tenants = getTenantsFromAuthentication(authentication);

    if (tenants != null && !tenants.isEmpty()) {
      return AuthenticatedTenants.assignedTenants(tenants);
    } else {
      return AuthenticatedTenants.noTenantsAssigned();
    }
  }

  private TenantAwareAuthentication getCurrentTenantAwareAuthentication() {
    final var authentication = SecurityContextHolder.getContext().getAuthentication();
    final TenantAwareAuthentication currentAuthentication;

    if (authentication instanceof TenantAwareAuthentication tenantAwareAuthentication) {
      currentAuthentication = tenantAwareAuthentication;
    } else {
      currentAuthentication = null;

      // log error message for visibility
      final var message = String.format("Multi Tenancy is not supported with current authentication type %s", authentication.getClass());
      LOGGER.error(message, new OperateRuntimeException());
    }

    return currentAuthentication;
  }

  private List<String> getTenantsFromAuthentication(final TenantAwareAuthentication authentication) {
    final var authenticatedTenants = new ArrayList<String>();

    if (authentication != null) {
      final var tenants = authentication.getTenants();
      if (tenants != null && !tenants.isEmpty()) {
        tenants.stream()
            .map(OperateTenant::getTenantId)
            .collect(Collectors.toCollection(() -> authenticatedTenants));
      }
    }

    return authenticatedTenants;
  }

  private boolean isMultiTenancyEnabled() {
    return operateProperties.getMultiTenancy().isEnabled()
        && SecurityContextHolder.getContext().getAuthentication()!=null;
  }

  public static class AuthenticatedTenants {

    private final TenantAccessType tenantAccessType;
    private final List<String> ids;

    AuthenticatedTenants(final TenantAccessType tenantAccessType, final List<String> ids) {
      this.tenantAccessType = tenantAccessType;
      this.ids = ids;
    }

    public TenantAccessType getTenantAccessType() {
      return tenantAccessType;
    }

    public List<String> getTenantIds() {
      return ids;
    }

    public static AuthenticatedTenants allTenants() {
      return new AuthenticatedTenants(TenantAccessType.TENANT_ACCESS_ALL, null);
    }

    public static AuthenticatedTenants noTenantsAssigned() {
      return new AuthenticatedTenants(TenantAccessType.TENANT_ACCESS_NONE, null);
    }

    public static AuthenticatedTenants assignedTenants(List<String> tenants) {
      return new AuthenticatedTenants(TenantAccessType.TENANT_ACCESS_ASSIGNED, tenants);
    }
  }

  public static enum TenantAccessType {
    TENANT_ACCESS_ALL,
    TENANT_ACCESS_ASSIGNED,
    TENANT_ACCESS_NONE
  }

}
