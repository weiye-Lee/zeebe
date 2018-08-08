/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.operate.it;

import java.util.Collections;
import java.util.List;
import org.apache.http.HttpStatus;
import org.camunda.operate.entities.IncidentState;
import org.camunda.operate.entities.OperationState;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.es.types.WorkflowInstanceType;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.rest.dto.IncidentDto;
import org.camunda.operate.rest.dto.OperationDto;
import org.camunda.operate.rest.dto.WorkflowInstanceBatchOperationDto;
import org.camunda.operate.rest.dto.WorkflowInstanceQueryDto;
import org.camunda.operate.rest.dto.WorkflowInstanceRequestDto;
import org.camunda.operate.rest.dto.WorkflowInstanceResponseDto;
import org.camunda.operate.util.ElasticsearchTestRule;
import org.camunda.operate.util.MockMvcTestRule;
import org.camunda.operate.util.OperateIntegrationTest;
import org.camunda.operate.util.ZeebeTestRule;
import org.camunda.operate.util.ZeebeUtil;
import org.camunda.operate.zeebe.operation.OperationExecutor;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import com.fasterxml.jackson.core.type.TypeReference;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.rest.WorkflowInstanceRestService.WORKFLOW_INSTANCE_URL;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
public class OperationIT extends OperateIntegrationTest {

  private static final String POST_BATCH_OPERATION_URL = "/api/workflow-instances/operation";
  private static final String QUERY_INSTANCES_URL = WORKFLOW_INSTANCE_URL;

  @Rule
  public ZeebeTestRule zeebeTestRule = new ZeebeTestRule();

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  @Autowired
  public ZeebeUtil zeebeUtil;

  @Rule
  public MockMvcTestRule mockMvcTestRule = new MockMvcTestRule();

  private MockMvc mockMvc;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private OperationExecutor operationExecutor;

  private Long initialBatchOperationMaxSize;

  @Before
  public void starting() {
    this.mockMvc = mockMvcTestRule.getMockMvc();
    this.initialBatchOperationMaxSize = operateProperties.getBatchOperationMaxSize();
    zeebeUtil.deployWorkflowToTheTopic(zeebeTestRule.getTopicName(), "demoProcess_v_1.bpmn");
  }

  @After
  public void after() {
    operateProperties.setBatchOperationMaxSize(initialBatchOperationMaxSize);
  }

  @Test
  public void testOperationsPersisted() throws Exception {
    // given
    final int instanceCount = 10;
    for (int i = 0; i<instanceCount; i++) {
      startDemoWorkflowInstance();
    }

    //when
    final WorkflowInstanceQueryDto allRunningQuery = createAllRunningQuery();
    postUpdateRetriesOperationWithOKResponse(allRunningQuery);

    //then
    WorkflowInstanceResponseDto response = getWorkflowInstances(allRunningQuery);

    assertThat(response.getWorkflowInstances()).hasSize(instanceCount);
    assertThat(response.getWorkflowInstances()).flatExtracting(WorkflowInstanceType.OPERATIONS).extracting(WorkflowInstanceType.TYPE).containsOnly(OperationType.UPDATE_RETRIES);
    assertThat(response.getWorkflowInstances()).flatExtracting(WorkflowInstanceType.OPERATIONS).extracting(WorkflowInstanceType.STATE).containsOnly(
      OperationState.SCHEDULED);
    assertThat(response.getWorkflowInstances()).flatExtracting(WorkflowInstanceType.OPERATIONS).extracting(WorkflowInstanceType.START_DATE).doesNotContainNull();
    assertThat(response.getWorkflowInstances()).flatExtracting(WorkflowInstanceType.OPERATIONS).extracting(WorkflowInstanceType.END_DATE).containsOnlyNulls();
  }

  @Test
  public void testOperationExecutedOnOneInstance() throws Exception {
    // given
    final String workflowInstanceId = startDemoWorkflowInstance();
    failTaskWithNoRetriesLeft("taskA");

    //when
    //we call UPDATE_RETRIES operation on instance
    final WorkflowInstanceQueryDto workflowInstanceQuery = createAllRunningQuery();
    workflowInstanceQuery.setIds(Collections.singletonList(workflowInstanceId));
    postUpdateRetriesOperationWithOKResponse(workflowInstanceQuery);

    //and execute the operation
    operationExecutor.executeOneBatch();

    //then
    //before we process messages from Zeebe, the state of the operation must be SENT
    WorkflowInstanceResponseDto workflowInstances = getWorkflowInstances(workflowInstanceQuery);

    assertThat(workflowInstances.getWorkflowInstances()).hasSize(1);
    assertThat(workflowInstances.getWorkflowInstances().get(0).getOperations()).hasSize(1);
    OperationDto operation = workflowInstances.getWorkflowInstances().get(0).getOperations().get(0);
    assertThat(operation.getType()).isEqualTo(OperationType.UPDATE_RETRIES);
    assertThat(operation.getState()).isEqualTo(OperationState.SENT);
    assertThat(operation.getStartDate()).isNotNull();
    assertThat(operation.getEndDate()).isNull();

    //after we process messages from Zeebe, the state of the operation is changed to COMPLETED
    elasticsearchTestRule.processAllEvents(3);
    workflowInstances = getWorkflowInstances(workflowInstanceQuery);
    assertThat(workflowInstances.getWorkflowInstances()).hasSize(1);
    assertThat(workflowInstances.getWorkflowInstances().get(0).getOperations()).hasSize(1);
    operation = workflowInstances.getWorkflowInstances().get(0).getOperations().get(0);
    assertThat(operation.getType()).isEqualTo(OperationType.UPDATE_RETRIES);
    assertThat(operation.getState()).isEqualTo(OperationState.COMPLETED);
    assertThat(operation.getStartDate()).isNotNull();
    assertThat(operation.getEndDate()).isNotNull();
    //assert that incident is resolved
    assertThat(workflowInstances.getWorkflowInstances().get(0).getIncidents()).hasSize(1);
    final IncidentDto incident = workflowInstances.getWorkflowInstances().get(0).getIncidents().get(0);
    assertThat(incident.getState()).isEqualTo(IncidentState.DELETED);
  }

  @Test
  public void testTwoOperationsOnOneInstance() throws Exception {
    // given
    final String workflowInstanceId = startDemoWorkflowInstance();
    failTaskWithNoRetriesLeft("taskA");

    //when we call UPDATE_RETRIES operation two times on one instance
    final WorkflowInstanceQueryDto workflowInstanceQuery = createAllRunningQuery();
    workflowInstanceQuery.setIds(Collections.singletonList(workflowInstanceId));
    postUpdateRetriesOperationWithOKResponse(workflowInstanceQuery);  //#1
    postUpdateRetriesOperationWithOKResponse(workflowInstanceQuery);  //#2

    //and execute the operation
    operationExecutor.executeOneBatch();

    //then
    //the state of one operation is COMPLETED and of the other - SENT
    elasticsearchTestRule.processAllEvents(3);
    WorkflowInstanceResponseDto workflowInstances = getWorkflowInstances(workflowInstanceQuery);
    assertThat(workflowInstances.getWorkflowInstances()).hasSize(1);
    final List<OperationDto> operations = workflowInstances.getWorkflowInstances().get(0).getOperations();
    assertThat(operations).hasSize(2);
    assertThat(operations).filteredOn(op -> op.getState().equals(OperationState.COMPLETED)).hasSize(1);
    assertThat(operations).filteredOn(op -> op.getState().equals(OperationState.SENT)).hasSize(1);
  }

  @Test
  public void testFailUpdateRetriesBecauseOfNoIncidents() throws Exception {
    // given
    final String workflowInstanceId = startDemoWorkflowInstance();

    //when
    //we call UPDATE_RETRIES operation on instance
    final WorkflowInstanceQueryDto workflowInstanceQuery = createAllRunningQuery();
    workflowInstanceQuery.setIds(Collections.singletonList(workflowInstanceId));
    postUpdateRetriesOperationWithOKResponse(workflowInstanceQuery);

    //and execute the operation
    operationExecutor.executeOneBatch();

    //then
    //the state of operation is FAILED, as there are no appropriate incidents
    WorkflowInstanceResponseDto workflowInstances = getWorkflowInstances(workflowInstanceQuery);
    assertThat(workflowInstances.getWorkflowInstances()).hasSize(1);
    assertThat(workflowInstances.getWorkflowInstances().get(0).getOperations()).hasSize(1);
    OperationDto operation = workflowInstances.getWorkflowInstances().get(0).getOperations().get(0);
    assertThat(operation.getState()).isEqualTo(OperationState.FAILED);
    assertThat(operation.getErrorMessage()).isEqualTo("No appropriate incidents found.");
    assertThat(operation.getEndDate()).isNotNull();
    assertThat(operation.getStartDate()).isNotNull();
  }

  @Test
  public void testFailOperationAsTooManyInstances() throws Exception {
    // given
    operateProperties.setBatchOperationMaxSize(5L);

    final int instanceCount = 10;
    for (int i = 0; i<instanceCount; i++) {
      startDemoWorkflowInstance();
    }

    //when
    final MvcResult mvcResult = postUpdateRetriesOperation(createAllRunningQuery(), HttpStatus.SC_BAD_REQUEST);

    final String expectedErrorMsg = String
      .format("Too many workflow instances are selected for batch operation. Maximum possible amount: %s", operateProperties.getBatchOperationMaxSize());
    assertThat(mvcResult.getResolvedException().getMessage()).isEqualTo(expectedErrorMsg);
  }

  private MvcResult postUpdateRetriesOperationWithOKResponse(WorkflowInstanceQueryDto query) throws Exception {
    return postUpdateRetriesOperation(query, HttpStatus.SC_OK);
  }

  private MvcResult postUpdateRetriesOperation(WorkflowInstanceQueryDto query, int expectedStatus) throws Exception {
    WorkflowInstanceBatchOperationDto batchOperationDto = createBatchOperationDto(OperationType.UPDATE_RETRIES, query);
    MockHttpServletRequestBuilder postOperationRequest =
      post(POST_BATCH_OPERATION_URL)
        .content(mockMvcTestRule.json(batchOperationDto))
        .contentType(mockMvcTestRule.getContentType());

    final MvcResult mvcResult =
      mockMvc.perform(postOperationRequest)
        .andExpect(status().is(expectedStatus))
        .andReturn();
    elasticsearchTestRule.refreshIndexesInElasticsearch();
    return mvcResult;
  }


  private WorkflowInstanceBatchOperationDto createBatchOperationDto(OperationType operationType, WorkflowInstanceQueryDto query) {
    WorkflowInstanceBatchOperationDto batchOperationDto = new WorkflowInstanceBatchOperationDto();
    batchOperationDto.getQueries().add(query);
    batchOperationDto.setOperationType(operationType);
    return batchOperationDto;
  }

  private void failTaskWithNoRetriesLeft(String taskName) {
    zeebeTestRule.setJobWorker(zeebeUtil.failTask(zeebeTestRule.getTopicName(), taskName, zeebeTestRule.getWorkerName(), 3));
    elasticsearchTestRule.processAllEvents(20);
  }

  private String startDemoWorkflowInstance() {
    String processId = "demoProcess";
    final String workflowInstanceId = zeebeUtil.startWorkflowInstance(zeebeTestRule.getTopicName(), processId, "{\"a\": \"b\"}");

    elasticsearchTestRule.processAllEvents(10);
    elasticsearchTestRule.refreshIndexesInElasticsearch();
    return workflowInstanceId;
  }

  private WorkflowInstanceResponseDto getWorkflowInstances(WorkflowInstanceQueryDto query) throws Exception {
    WorkflowInstanceRequestDto request = new WorkflowInstanceRequestDto();
    request.getQueries().add(query);
    MockHttpServletRequestBuilder getWorkflowInstancesRequest =
      post(query(0, 100)).content(mockMvcTestRule.json(request))
        .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult =
      mockMvc.perform(getWorkflowInstancesRequest)
        .andExpect(status().isOk())
        .andExpect(content().contentType(mockMvcTestRule.getContentType()))
        .andReturn();

    return mockMvcTestRule.fromResponse(mvcResult, new TypeReference<WorkflowInstanceResponseDto>() {
    });
  }

  private WorkflowInstanceQueryDto createAllRunningQuery() {
    WorkflowInstanceQueryDto query = new WorkflowInstanceQueryDto();
    query.setRunning(true);
    query.setActive(true);
    query.setIncidents(true);
    return query;
  }

  private String query(int firstResult, int maxResults) {
    return String.format("%s?firstResult=%d&maxResults=%d", QUERY_INSTANCES_URL, firstResult, maxResults);
  }

}
