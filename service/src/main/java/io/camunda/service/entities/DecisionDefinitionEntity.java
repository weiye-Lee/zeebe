/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DecisionDefinitionEntity(
    String tenantId,
    Long key,
    String id,
    String name,
    Integer version,
    String decisionRequirementsId,
    Long decisionRequirementsKey,
    String decisionId,
    String decisionRequirementsName,
    Integer decisionRequirementsVersion) {}