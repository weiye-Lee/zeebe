/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp.security;

import java.util.Arrays;

public enum Role {
  OWNER,
  OPERATOR,
  READER;

  public static Role fromString(final String roleAsString) {
    final String roleName = roleAsString.replaceAll("\\s+", "_");
    for (Role role : values()) {
      if (role.name().equalsIgnoreCase(roleName)) {
        return role;
      }
    }
    throw new IllegalArgumentException(
        String.format("%s does not exists as Role in %s", roleAsString, Arrays.toString(values())));
  }
}
