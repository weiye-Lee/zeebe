/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.elasticsearch.reader;

import io.camunda.operate.cache.ProcessCache;
import io.camunda.operate.entities.ErrorType;
import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.store.FlowNodeStore;
import io.camunda.operate.store.IncidentStore;
import io.camunda.operate.util.TreePath;
import io.camunda.operate.webapp.data.IncidentDataHolder;
import io.camunda.operate.webapp.reader.OperationReader;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentErrorTypeDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentFlowNodeDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static io.camunda.operate.webapp.rest.dto.incidents.IncidentDto.FALLBACK_PROCESS_DEFINITION_NAME;

@Profile("!opensearch")
@Component
public class IncidentReader extends AbstractReader implements io.camunda.operate.webapp.reader.IncidentReader {

  private static final Logger logger = LoggerFactory.getLogger(IncidentReader.class);

  @Autowired
  private OperationReader operationReader;

  @Autowired
  private ProcessInstanceReader processInstanceReader;

  @Autowired
  private ProcessCache processCache;

  @Autowired
  private IncidentStore incidentStore;

  @Autowired
  private FlowNodeStore flowNodeStore;

  @Override
  public List<IncidentEntity> getAllIncidentsByProcessInstanceKey(Long processInstanceKey) {
    return incidentStore.getIncidentsByProcessInstanceKey(processInstanceKey);
  }

  /**
   * Returns map of incident ids per process instance id.
   * @param processInstanceKeys
   * @return
   */
  @Override
  public Map<Long, List<Long>> getIncidentKeysPerProcessInstance(List<Long> processInstanceKeys) {
    return incidentStore.getIncidentKeysPerProcessInstance(processInstanceKeys);
  }

  @Override
  public IncidentEntity getIncidentById(Long incidentKey) {
    return incidentStore.getIncidentById(incidentKey);
  }

  @Override
  public IncidentResponseDto getIncidentsByProcessInstanceId(String processInstanceId) {
    //get treePath for process instance
    final String treePath = processInstanceReader.getProcessInstanceTreePath(processInstanceId);

    List<Map<ErrorType, Long>> errorTypes = new ArrayList<>();
    List<IncidentEntity> incidents = incidentStore.getIncidentsWithErrorTypesFor(treePath, errorTypes);

    final IncidentResponseDto incidentResponse = new IncidentResponseDto();
    incidentResponse.setErrorTypes(errorTypes.stream().map(m -> {
      var entry = m.entrySet().iterator().next();
      return IncidentErrorTypeDto.createFrom(entry.getKey()).setCount(entry.getValue().intValue());
    }).collect(Collectors.toList()));

    final Map<Long, String> processNames = new HashMap<>();
    incidents.stream().filter(inc -> processNames.get(inc.getProcessDefinitionKey()) == null).forEach(
        inc -> processNames.put(inc.getProcessDefinitionKey(),
            processCache.getProcessNameOrBpmnProcessId(inc.getProcessDefinitionKey(),
                FALLBACK_PROCESS_DEFINITION_NAME)));

    final Map<Long, List<OperationEntity>> operations = operationReader.getOperationsPerIncidentKey(processInstanceId);

    final Map<String, IncidentDataHolder> incData = collectFlowNodeDataForPropagatedIncidents(incidents,
        processInstanceId, treePath);

    //collect flow node statistics
    incidentResponse.setFlowNodes(incData.values().stream()
        .collect(Collectors.groupingBy(IncidentDataHolder::getFinalFlowNodeId, Collectors.counting())).entrySet()
        .stream().map(entry -> new IncidentFlowNodeDto(entry.getKey(), entry.getValue().intValue()))
        .collect(Collectors.toList()));

    final List<IncidentDto> incidentsDtos = IncidentDto.sortDefault(
        IncidentDto.createFrom(incidents, operations, processNames, incData));
    incidentResponse.setIncidents(incidentsDtos);
    incidentResponse.setCount(incidents.size());
    return incidentResponse;
  }

  /**
   * Returns map incidentId -> IncidentDataHolder.
   * @param incidents
   * @param processInstanceId
   * @param currentTreePath
   * @return
   */
  @Override
  public Map<String, IncidentDataHolder> collectFlowNodeDataForPropagatedIncidents(
          final List<IncidentEntity> incidents, String processInstanceId, String currentTreePath) {

    final Set<String> flowNodeInstanceIdsSet = new HashSet<>();
    final Map<String, IncidentDataHolder> incDatas = new HashMap<>();
    for (IncidentEntity inc: incidents) {
      IncidentDataHolder incData = new IncidentDataHolder().setIncidentId(inc.getId());
      if (!String.valueOf(inc.getProcessInstanceKey()).equals(processInstanceId)) {
        final String callActivityInstanceId = TreePath
            .extractFlowNodeInstanceId(inc.getTreePath(), currentTreePath);
        incData.setFinalFlowNodeInstanceId(callActivityInstanceId);
        flowNodeInstanceIdsSet.add(callActivityInstanceId);
      } else {
        incData.setFinalFlowNodeInstanceId(String.valueOf(inc.getFlowNodeInstanceKey()));
        incData.setFinalFlowNodeId(inc.getFlowNodeId());
      }
      incDatas.put(inc.getId(), incData);
    }

    if (flowNodeInstanceIdsSet.size() > 0) {
      //select flowNodeIds by flowNodeInstanceIds
      final Map<String, String> flowNodeIdsMap = flowNodeStore.getFlowNodeIdsForFlowNodeInstances(flowNodeInstanceIdsSet);

      //set flow node id, where not yet set
      incDatas.values().stream()
          .filter(iData -> iData.getFinalFlowNodeId() == null)
          .forEach(iData -> iData
              .setFinalFlowNodeId(flowNodeIdsMap.get(iData.getFinalFlowNodeInstanceId())));
    }
    return incDatas;
  }

}
