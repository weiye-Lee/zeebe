/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {FlowNodeInstancesTree} from '../index';

import {
  eventSubProcessFlowNodeInstances,
  mockFlowNodeInstance,
  processId,
  processInstanceId,
  Wrapper,
  eventSubprocessProcessInstance,
} from './mocks';
import {eventSubProcess} from 'modules/testUtils';
import {createRef} from 'react';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {mockFetchFlowNodeInstances} from 'modules/mocks/api/fetchFlowNodeInstances';

describe('FlowNodeInstancesTree - Event Subprocess', () => {
  beforeEach(async () => {
    mockFetchProcessInstance().withSuccess(eventSubprocessProcessInstance);
    mockFetchProcessXML().withSuccess(eventSubProcess);

    await processInstanceDetailsDiagramStore.fetchProcessXml(processId);

    processInstanceDetailsStore.init({id: processInstanceId});
    flowNodeInstanceStore.init();
  });

  it('should be able to unfold and fold event subprocesses', async () => {
    mockFetchFlowNodeInstances().withSuccess(
      eventSubProcessFlowNodeInstances.level1
    );

    await waitFor(() => {
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
    });

    const {user} = render(
      <FlowNodeInstancesTree
        flowNodeInstance={{...mockFlowNodeInstance, state: 'ACTIVE'}}
        scrollableContainerRef={createRef<HTMLElement>()}
        isRoot
      />,
      {
        wrapper: Wrapper,
      }
    );

    expect(
      screen.queryByLabelText('Event Subprocess', {
        selector: "[aria-expanded='true']",
      })
    ).not.toBeInTheDocument();
    expect(screen.queryByText('Interrupting timer')).not.toBeInTheDocument();

    mockFetchFlowNodeInstances().withSuccess(
      eventSubProcessFlowNodeInstances.level2
    );

    await user.type(
      screen.getByLabelText('Event Subprocess', {
        selector: "[aria-expanded='false']",
      }),
      '{arrowright}'
    );

    expect(
      await screen.findByLabelText('Event Subprocess', {
        selector: "[aria-expanded='true']",
      })
    ).toBeInTheDocument();

    expect(await screen.findByText('Interrupting timer')).toBeInTheDocument();

    await user.type(
      screen.getByLabelText('Event Subprocess', {
        selector: "[aria-expanded='true']",
      }),
      '{arrowleft}'
    );

    expect(screen.queryByText('Interrupting timer')).not.toBeInTheDocument();
  });
});
