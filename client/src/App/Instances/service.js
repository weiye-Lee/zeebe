/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {isEmpty} from 'lodash';

import {parseDiagramXML} from 'modules/utils/bpmn';

import {getWorkflowByVersion} from 'modules/utils/filter';

export function getStateUpdateForAddSelection(
  selection,
  rollingSelectionIndex,
  instancesInSelectionsCount,
  selectionCount,
  prevState
) {
  const currentSelectionIndex = rollingSelectionIndex + 1;
  const newCount = instancesInSelectionsCount + selection.totalCount;
  return {
    selections: [
      {
        selectionId: currentSelectionIndex,
        ...selection
      },
      ...prevState.selections
    ],
    rollingSelectionIndex: currentSelectionIndex,
    instancesInSelectionsCount: newCount,
    selectionCount: selectionCount + 1,
    openSelection: currentSelectionIndex,
    selection: {all: false, ids: [], excludeIds: []}
  };
}

export function decodeFields(object) {
  let result = {};

  for (let key in object) {
    const value = object[key];
    result[key] = typeof value === 'string' ? decodeURI(object[key]) : value;
  }
  return result;
}

export function getWorkflowName(workflow) {
  return workflow ? workflow.name || workflow.bpmnProcessId : 'Workflow';
}

export async function fetchDiagramModel(dataManager, workflowId) {
  const xml = await dataManager.getWorkflowXML(workflowId);
  return await parseDiagramXML(xml);
}

export function getWorkflowByVersionFromFilter({
  filter: {workflow, version},
  groupedWorkflows
}) {
  return getWorkflowByVersion(groupedWorkflows[workflow], version);
}

export function getWorkflowNameFromFilter({filter, groupedWorkflows}) {
  const currentWorkflowByVersion = getWorkflowByVersionFromFilter({
    filter,
    groupedWorkflows
  });

  if (!isEmpty(currentWorkflowByVersion)) {
    return getWorkflowName(currentWorkflowByVersion);
  }

  const currentWorkflow = groupedWorkflows[filter.workflow];
  return getWorkflowName(currentWorkflow);
}
