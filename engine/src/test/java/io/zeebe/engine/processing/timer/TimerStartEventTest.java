/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.timer;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.engine.state.instance.TimerInstance;
import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.ProcessBuilder;
import io.zeebe.protocol.record.Assertions;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.zeebe.protocol.record.intent.TimerIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.zeebe.protocol.record.value.TimerRecordValue;
import io.zeebe.protocol.record.value.deployment.DeployedProcess;
import io.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.Rule;
import org.junit.Test;

public final class TimerStartEventTest {

  private static final BpmnModelInstance SIMPLE_MODEL =
      Bpmn.createExecutableProcess("process")
          .startEvent("start_1")
          .timerWithCycle("R1/PT1S")
          .endEvent("end_1")
          .done();

  private static final BpmnModelInstance REPEATING_MODEL =
      Bpmn.createExecutableProcess("process")
          .startEvent("start_2")
          .timerWithCycle("R/PT1S")
          .endEvent("end_2")
          .done();

  private static final BpmnModelInstance THREE_SEC_MODEL =
      Bpmn.createExecutableProcess("process_3")
          .startEvent("start_3")
          .timerWithCycle("R2/PT3S")
          .endEvent("end_3")
          .done();

  private static final BpmnModelInstance MULTIPLE_START_EVENTS_MODEL =
      createTimerAndMessageStartEventsModel();

  private static final BpmnModelInstance MULTI_TIMER_START_MODEL = createMultipleTimerStartModel();

  private static final BpmnModelInstance FEEL_DATE_TIME_EXPRESSION_MODEL =
      Bpmn.createExecutableProcess("process_5")
          .startEvent("start_5")
          .timerWithDateExpression("date and time(date(\"3978-11-25\"),time(\"T00:00:00@UTC\"))")
          .endEvent("end_5")
          .done();

  private static final BpmnModelInstance FEEL_CYCLE_EXPRESSION_MODEL =
      Bpmn.createExecutableProcess("process_5")
          .startEvent("start_6")
          .timerWithCycleExpression("cycle(duration(\"PT1S\"))")
          .endEvent("end_6")
          .done();

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  private static BpmnModelInstance createTimerAndMessageStartEventsModel() {
    final ProcessBuilder builder = Bpmn.createExecutableProcess("process");
    builder.startEvent("none_start").endEvent("none_end");
    builder.startEvent("timer_start").timerWithCycle("R1/PT1S").endEvent("timer_end");
    return builder.startEvent("msg_start").message("msg1").endEvent("msg_end").done();
  }

  private static BpmnModelInstance createMultipleTimerStartModel() {
    final ProcessBuilder builder = Bpmn.createExecutableProcess("process_4");
    builder.startEvent("start_4").timerWithCycle("R/PT2S").endEvent("end_4");
    return builder.startEvent("start_4_2").timerWithCycle("R/PT3S").endEvent("end_4_2").done();
  }

  @Test
  public void shouldCreateTimer() {
    // when
    final DeployedProcess deployedProcess =
        engine
            .deployment()
            .withXmlResource(SIMPLE_MODEL)
            .deploy()
            .getValue()
            .getDeployedProcesses()
            .get(0);

    // then
    final TimerRecordValue timerRecord =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessDefinitionKey(deployedProcess.getProcessDefinitionKey())
            .getFirst()
            .getValue();

    Assertions.assertThat(timerRecord)
        .hasProcessInstanceKey(TimerInstance.NO_ELEMENT_INSTANCE)
        .hasTargetElementId("start_1")
        .hasElementInstanceKey(TimerInstance.NO_ELEMENT_INSTANCE);

    final long now = engine.getClock().getCurrentTimeInMillis();
    assertThat(timerRecord.getDueDate()).isBetween(now, now + 1000L);
  }

  @Test
  public void shouldCreateTimerFromFeelExpression() {
    // when
    final DeployedProcess deployedProcess =
        engine
            .deployment()
            .withXmlResource(FEEL_DATE_TIME_EXPRESSION_MODEL)
            .deploy()
            .getValue()
            .getDeployedProcesses()
            .get(0);

    // then
    final TimerRecordValue timerRecord =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessDefinitionKey(deployedProcess.getProcessDefinitionKey())
            .getFirst()
            .getValue();

    Assertions.assertThat(timerRecord)
        .hasProcessInstanceKey(TimerInstance.NO_ELEMENT_INSTANCE)
        .hasTargetElementId("start_5")
        .hasElementInstanceKey(TimerInstance.NO_ELEMENT_INSTANCE);

    final long expected =
        ZonedDateTime.of(LocalDate.of(3978, 11, 25), LocalTime.of(0, 0, 0), ZoneId.of("UTC"))
            .toInstant()
            .toEpochMilli();
    assertThat(timerRecord.getDueDate()).isEqualTo(expected);
  }

  @Test
  public void shouldCreateRepeatingTimerFromFeelExpression() {
    // when
    final DeployedProcess deployedProcess =
        engine
            .deployment()
            .withXmlResource(FEEL_CYCLE_EXPRESSION_MODEL)
            .deploy()
            .getValue()
            .getDeployedProcesses()
            .get(0);

    // then
    final TimerRecordValue timerRecord =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessDefinitionKey(deployedProcess.getProcessDefinitionKey())
            .getFirst()
            .getValue();

    Assertions.assertThat(timerRecord)
        .hasProcessInstanceKey(TimerInstance.NO_ELEMENT_INSTANCE)
        .hasTargetElementId("start_6")
        .hasElementInstanceKey(TimerInstance.NO_ELEMENT_INSTANCE);

    final long now = engine.getClock().getCurrentTimeInMillis();
    assertThat(timerRecord.getDueDate()).isBetween(now, now + 10000L);
  }

  @Test
  public void shouldTriggerAndCreateProcessInstance() {
    // given
    final DeployedProcess deployedProcess =
        engine
            .deployment()
            .withXmlResource(SIMPLE_MODEL)
            .deploy()
            .getValue()
            .getDeployedProcesses()
            .get(0);

    final long processDefinitionKey = deployedProcess.getProcessDefinitionKey();
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessDefinitionKey(processDefinitionKey)
                .exists())
        .isTrue();

    // when
    engine.increaseTime(Duration.ofSeconds(2));

    // then
    final ProcessInstanceRecordValue startEventActivating =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.START_EVENT)
            .withProcessDefinitionKey(processDefinitionKey)
            .getFirst()
            .getValue();

    Assertions.assertThat(startEventActivating)
        .hasElementId("start_1")
        .hasBpmnProcessId("process")
        .hasVersion(deployedProcess.getVersion())
        .hasProcessDefinitionKey(processDefinitionKey);

    final long triggerRecordPosition =
        RecordingExporter.timerRecords(TimerIntent.TRIGGER)
            .withProcessDefinitionKey(processDefinitionKey)
            .getFirst()
            .getPosition();

    assertThat(
            RecordingExporter.timerRecords()
                .withProcessDefinitionKey(processDefinitionKey)
                .skipUntil(r -> r.getPosition() >= triggerRecordPosition)
                .limit(2))
        .extracting(Record::getIntent)
        .containsExactly(TimerIntent.TRIGGER, TimerIntent.TRIGGERED);

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessDefinitionKey(processDefinitionKey)
                .skipUntil(r -> r.getPosition() >= triggerRecordPosition)
                .limit(4))
        .extracting(Record::getIntent)
        .containsExactly(
            ProcessInstanceIntent.ACTIVATE_ELEMENT, // causes the flow node activation
            ProcessInstanceIntent.ELEMENT_ACTIVATING, // causes the flow node activation
            ProcessInstanceIntent.ELEMENT_ACTIVATED, // input mappings applied
            ProcessInstanceIntent.ACTIVATE_ELEMENT); // triggers the start event
  }

  @Test
  public void shouldCreateMultipleProcessInstancesWithRepeatingTimer() {
    // given
    final DeployedProcess deployedProcess =
        engine
            .deployment()
            .withXmlResource(THREE_SEC_MODEL)
            .deploy()
            .getValue()
            .getDeployedProcesses()
            .get(0);
    final long processDefinitionKey = deployedProcess.getProcessDefinitionKey();

    // when
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessDefinitionKey(processDefinitionKey)
                .exists())
        .isTrue();
    engine.increaseTime(Duration.ofSeconds(3));

    // then
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
                .withProcessDefinitionKey(processDefinitionKey)
                .exists())
        .isTrue();
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .withElementId("process_3")
                .withProcessDefinitionKey(processDefinitionKey)
                .exists())
        .isTrue();
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessDefinitionKey(processDefinitionKey)
                .limit(2)
                .count())
        .isEqualTo(2);

    // when
    engine.increaseTime(Duration.ofSeconds(3));

    // then
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
                .withProcessDefinitionKey(processDefinitionKey)
                .limit(2)
                .count())
        .isEqualTo(2);
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .withElementId("process_3")
                .withProcessDefinitionKey(processDefinitionKey)
                .limit(2)
                .count())
        .isEqualTo(2);
  }

  @Test
  public void shouldCompleteProcess() {
    // given
    final DeployedProcess deployedProcess =
        engine
            .deployment()
            .withXmlResource(SIMPLE_MODEL)
            .deploy()
            .getValue()
            .getDeployedProcesses()
            .get(0);
    assertThat(RecordingExporter.timerRecords(TimerIntent.CREATED).exists()).isTrue();

    // when
    engine.increaseTime(Duration.ofSeconds(1));

    // then
    final ProcessInstanceRecordValue instanceCompleted =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .withProcessDefinitionKey(deployedProcess.getProcessDefinitionKey())
            .withElementId("process")
            .getFirst()
            .getValue();

    Assertions.assertThat(instanceCompleted)
        .hasBpmnProcessId("process")
        .hasVersion(1)
        .hasProcessDefinitionKey(deployedProcess.getProcessDefinitionKey());
  }

  @Test
  public void shouldUpdateProcess() {
    // when
    final DeployedProcess deployedProcess =
        engine
            .deployment()
            .withXmlResource(SIMPLE_MODEL)
            .deploy()
            .getValue()
            .getDeployedProcesses()
            .get(0);
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessDefinitionKey(deployedProcess.getProcessDefinitionKey())
                .exists())
        .isTrue();
    engine.increaseTime(Duration.ofSeconds(1));

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withElementId("end_1")
                .withBpmnProcessId("process")
                .withProcessDefinitionKey(deployedProcess.getProcessDefinitionKey())
                .withVersion(1)
                .exists())
        .isTrue();

    // when
    final DeployedProcess repeatingProcess =
        engine
            .deployment()
            .withXmlResource(REPEATING_MODEL)
            .deploy()
            .getValue()
            .getDeployedProcesses()
            .get(0);
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessDefinitionKey(repeatingProcess.getProcessDefinitionKey())
                .exists())
        .isTrue();
    engine.increaseTime(Duration.ofSeconds(2));

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withElementId("end_2")
                .withBpmnProcessId("process")
                .withProcessDefinitionKey(repeatingProcess.getProcessDefinitionKey())
                .withVersion(2)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldReplaceTimerStartWithNoneStart() {
    // when
    final DeployedProcess repeatingProcess =
        engine
            .deployment()
            .withXmlResource(REPEATING_MODEL)
            .deploy()
            .getValue()
            .getDeployedProcesses()
            .get(0);
    final long repeatingProcessDefinitionKey = repeatingProcess.getProcessDefinitionKey();
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessDefinitionKey(repeatingProcessDefinitionKey)
                .exists())
        .isTrue();
    engine.increaseTime(Duration.ofSeconds(1));

    // then
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
                .withProcessDefinitionKey(repeatingProcessDefinitionKey)
                .exists())
        .isTrue();

    // when
    final BpmnModelInstance nonTimerModel =
        Bpmn.createExecutableProcess("process").startEvent("start_4").endEvent("end_4").done();
    final DeployedProcess notTimerDeployment =
        engine
            .deployment()
            .withXmlResource(nonTimerModel)
            .deploy()
            .getValue()
            .getDeployedProcesses()
            .get(0);
    engine.increaseTime(Duration.ofSeconds(2));

    // then
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CANCELED)
                .withProcessDefinitionKey(repeatingProcessDefinitionKey)
                .exists())
        .isTrue();
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
                .withProcessDefinitionKey(repeatingProcessDefinitionKey)
                .exists())
        .isTrue();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId("process").create();

    final ProcessInstanceRecordValue lastRecord =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withElementId("end_4")
            .withProcessDefinitionKey(notTimerDeployment.getProcessDefinitionKey())
            .getFirst()
            .getValue();

    Assertions.assertThat(lastRecord)
        .hasVersion(2)
        .hasBpmnProcessId("process")
        .hasProcessInstanceKey(processInstanceKey);
  }

  @Test
  public void shouldUpdateTimerPeriod() {
    // given
    final DeployedProcess deployedProcess =
        engine
            .deployment()
            .withXmlResource(THREE_SEC_MODEL)
            .deploy()
            .getValue()
            .getDeployedProcesses()
            .get(0);

    final long processDefinitionKey = deployedProcess.getProcessDefinitionKey();
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessDefinitionKey(processDefinitionKey)
                .exists())
        .isTrue();

    // when
    final long now = engine.getClock().getCurrentTimeInMillis();
    engine.increaseTime(Duration.ofSeconds(3));

    // then
    TimerRecordValue timerRecord =
        RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
            .withProcessDefinitionKey(processDefinitionKey)
            .getFirst()
            .getValue();

    assertThat(timerRecord.getDueDate()).isBetween(now, now + 3000);

    // when
    final BpmnModelInstance slowerModel =
        Bpmn.createExecutableProcess("process_3")
            .startEvent("start_4")
            .timerWithCycle("R2/PT4S")
            .endEvent("end_4")
            .done();
    final DeployedProcess slowerDeployment =
        engine
            .deployment()
            .withXmlResource(slowerModel)
            .deploy()
            .getValue()
            .getDeployedProcesses()
            .get(0);

    // then
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CANCELED)
                .withProcessDefinitionKey(processDefinitionKey)
                .getFirst())
        .isNotNull();

    final Record<TimerRecordValue> slowTimerRecord =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessDefinitionKey(slowerDeployment.getProcessDefinitionKey())
            .getFirst();
    timerRecord = slowTimerRecord.getValue();
    final long writtenTime = slowTimerRecord.getTimestamp();
    assertThat(timerRecord.getDueDate()).isBetween(writtenTime, writtenTime + 4000);

    // when
    engine.increaseTime(Duration.ofSeconds(4));

    // then
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
                .withProcessDefinitionKey(slowerDeployment.getProcessDefinitionKey())
                .exists())
        .isTrue();
  }

  @Test
  public void shouldTriggerDifferentProcessesSeparately() {
    // given
    final DeployedProcess firstDeployment =
        engine
            .deployment()
            .withXmlResource(THREE_SEC_MODEL)
            .deploy()
            .getValue()
            .getDeployedProcesses()
            .get(0);

    final DeployedProcess secondDeployment =
        engine
            .deployment()
            .withXmlResource(REPEATING_MODEL)
            .deploy()
            .getValue()
            .getDeployedProcesses()
            .get(0);

    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessDefinitionKey(firstDeployment.getProcessDefinitionKey())
                .exists())
        .isTrue();

    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessDefinitionKey(secondDeployment.getProcessDefinitionKey())
                .exists())
        .isTrue();

    // when
    engine.increaseTime(Duration.ofSeconds(1));

    // then
    final long firstModelTimestamp =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementId("process")
            .getFirst()
            .getTimestamp();

    // when
    engine.increaseTime(Duration.ofSeconds(2));

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .withElementId("process")
                .withProcessDefinitionKey(secondDeployment.getProcessDefinitionKey())
                .limit(2)
                .count())
        .isEqualTo(2);

    final long secondModelTimestamp =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementId("process_3")
            .withProcessDefinitionKey(firstDeployment.getProcessDefinitionKey())
            .getFirst()
            .getTimestamp();
    assertThat(secondModelTimestamp).isGreaterThan(firstModelTimestamp);
  }

  @Test
  public void shouldCreateMultipleInstanceAtTheCorrectTimes() {
    // given
    final DeployedProcess deployedProcess =
        engine
            .deployment()
            .withXmlResource(MULTI_TIMER_START_MODEL)
            .deploy()
            .getValue()
            .getDeployedProcesses()
            .get(0);
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessDefinitionKey(deployedProcess.getProcessDefinitionKey())
                .limit(2)
                .count())
        .isEqualTo(2);

    // when
    engine.increaseTime(Duration.ofSeconds(2));

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withElementId("end_4")
                .withProcessDefinitionKey(deployedProcess.getProcessDefinitionKey())
                .exists())
        .isTrue();

    // when
    engine.increaseTime(Duration.ofSeconds(1));
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withElementId("end_4_2")
                .withProcessDefinitionKey(deployedProcess.getProcessDefinitionKey())
                .exists())
        .isTrue();
  }

  @Test
  public void shouldTriggerAtSpecifiedTimeDate() {
    // given
    final Instant triggerTime = Instant.now().plusMillis(2000);
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess("process")
            .startEvent("start_2")
            .timerWithDate(triggerTime.toString())
            .endEvent("end_2")
            .done();

    final DeployedProcess deployedProcess =
        engine
            .deployment()
            .withXmlResource(model)
            .deploy()
            .getValue()
            .getDeployedProcesses()
            .get(0);

    // when
    engine.increaseTime(Duration.ofSeconds(2));

    // then
    final TimerRecordValue timerRecord =
        RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
            .withProcessDefinitionKey(deployedProcess.getProcessDefinitionKey())
            .getFirst()
            .getValue();

    Assertions.assertThat(timerRecord)
        .hasDueDate(triggerTime.toEpochMilli())
        .hasTargetElementId("start_2")
        .hasElementInstanceKey(TimerInstance.NO_ELEMENT_INSTANCE);

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withElementId("end_2")
                .withProcessDefinitionKey(deployedProcess.getProcessDefinitionKey())
                .exists())
        .isTrue();
  }

  @Test
  public void shouldTriggerIfTimeDatePassedOnDeployment() {
    // given
    final Instant triggerTime = Instant.now().plusMillis(2000);
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess("process")
            .startEvent("start_2")
            .timerWithDate(triggerTime.toString())
            .endEvent("end_2")
            .done();

    final DeployedProcess deployedProcess =
        engine
            .deployment()
            .withXmlResource(model)
            .deploy()
            .getValue()
            .getDeployedProcesses()
            .get(0);

    // when
    engine.increaseTime(Duration.ofMillis(2000L));

    // then
    final TimerRecordValue timerRecord =
        RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
            .withProcessDefinitionKey(deployedProcess.getProcessDefinitionKey())
            .getFirst()
            .getValue();

    Assertions.assertThat(timerRecord)
        .hasDueDate(triggerTime.toEpochMilli())
        .hasTargetElementId("start_2")
        .hasElementInstanceKey(TimerInstance.NO_ELEMENT_INSTANCE);
  }

  @Test
  public void shouldTriggerOnlyTimerStartEvent() {
    // given
    final DeployedProcess deployedProcess =
        engine
            .deployment()
            .withXmlResource(MULTIPLE_START_EVENTS_MODEL)
            .deploy()
            .getValue()
            .getDeployedProcesses()
            .get(0);
    final long processDefinitionKey = deployedProcess.getProcessDefinitionKey();

    RecordingExporter.timerRecords(TimerIntent.CREATED)
        .withProcessDefinitionKey(processDefinitionKey)
        .await();

    // when
    engine.increaseTime(Duration.ofSeconds(1));

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessDefinitionKey(processDefinitionKey)
                .limitToProcessInstanceCompleted()
                .withElementType(BpmnElementType.START_EVENT))
        .extracting(r -> r.getValue().getElementId())
        .containsOnly("timer_start");
  }
}
