/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.reader;

import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.webapp.rest.dto.FlowNodeStatisticsDto;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceRequestDto;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceResponseDto;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeStateDto;
import io.camunda.operate.webapp.rest.dto.metadata.FlowNodeMetadataDto;
import io.camunda.operate.webapp.rest.dto.metadata.FlowNodeMetadataRequestDto;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface FlowNodeInstanceReader {

    String NUMBER_OF_INCIDENTS_FOR_TREE_PATH = "numberOfIncidentsForTreePath";
    String AGG_INCIDENT_PATHS = "aggIncidentPaths";
    String AGG_INCIDENTS = "incidents";
    String AGG_RUNNING_PARENT = "running";
    String LEVELS_AGG_NAME = "levelsAgg";
    String LEVELS_TOP_HITS_AGG_NAME = "levelsTopHitsAgg";
    String FINISHED_FLOW_NODES_BUCKETS_AGG_NAME = "finishedFlowNodesBuckets";
    String FLOW_NODE_ID_AGG = "flowNodeIdAgg";
    String COUNT_INCIDENT = "countIncident";
    String COUNT_CANCELED = "countCanceled";
    String COUNT_COMPLETED = "countCompleted";
    String COUNT_ACTIVE = "countActive";

    Map<String, FlowNodeInstanceResponseDto> getFlowNodeInstances(FlowNodeInstanceRequestDto request);

    FlowNodeMetadataDto getFlowNodeMetadata(String processInstanceId,
                                            FlowNodeMetadataRequestDto request);

    Map<String, FlowNodeStateDto> getFlowNodeStates(String processInstanceId);

    List<Long> getFlowNodeInstanceKeysByIdAndStates(Long processInstanceId, String flowNodeId, List<FlowNodeState> states);

    Collection<FlowNodeStatisticsDto> getFlowNodeStatisticsForProcessInstance(Long processInstanceId);
}
