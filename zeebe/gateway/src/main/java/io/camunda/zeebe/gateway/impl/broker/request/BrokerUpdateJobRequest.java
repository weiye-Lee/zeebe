/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

public class BrokerUpdateJobRequest extends BrokerExecuteCommand<JobRecord> {

  private final JobRecord requestDto = new JobRecord();

  public BrokerUpdateJobRequest(final long jobKey, final int retries, final long timeout) {
    super(ValueType.JOB, JobIntent.UPDATE);
    request.setKey(jobKey);
    requestDto.setRetries(retries);
    requestDto.setTimeout(timeout);
  }

  @Override
  public BufferWriter getRequestWriter() {
    return requestDto;
  }

  @Override
  protected JobRecord toResponseDto(final DirectBuffer buffer) {
    final JobRecord responseDto = new JobRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
