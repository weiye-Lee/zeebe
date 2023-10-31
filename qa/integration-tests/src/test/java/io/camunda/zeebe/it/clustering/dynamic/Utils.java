/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.clustering.dynamic;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.management.cluster.PostOperationResponse;
import io.camunda.zeebe.management.cluster.TopologyChange.StatusEnum;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import java.time.OffsetDateTime;

final class Utils {
  public static void assertChangeIsPlanned(final PostOperationResponse response) {
    assertThat(response.getPlannedChanges()).isNotEmpty();
    assertThat(response.getExpectedTopology())
        .usingRecursiveComparison()
        .ignoringFieldsOfTypes(OffsetDateTime.class)
        .isNotEqualTo(response.getCurrentTopology());
  }

  public static void assertChangeIsApplied(
      final TestCluster cluster, final PostOperationResponse response) {
    final var actuator = ClusterActuator.of(cluster.availableGateway());
    final var expectedTopology = response.getExpectedTopology();
    final var currentTopology = actuator.getTopology().getBrokers();
    assertThat(currentTopology)
        .usingRecursiveComparison()
        .ignoringFields("lastUpdatedAt")
        .isEqualTo(expectedTopology);
  }

  public static void assertChangeIsCompleted(
      final TestCluster cluster, final PostOperationResponse response) {
    final var actuator = ClusterActuator.of(cluster.availableGateway());
    final var currentChange = actuator.getTopology().getChange();
    assertThat(currentChange).isNotNull();
    assertThat(currentChange.getId()).isEqualTo(response.getChangeId());
    assertThat(currentChange.getStatus()).isEqualTo(StatusEnum.COMPLETED);
  }
}
