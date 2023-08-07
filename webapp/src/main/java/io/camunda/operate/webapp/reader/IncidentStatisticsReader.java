/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.reader;

import io.camunda.operate.entities.listview.ProcessInstanceState;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentsByErrorMsgStatisticsDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentsByProcessGroupStatisticsDto;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;

import java.util.Set;

import static io.camunda.operate.schema.templates.ListViewTemplate.*;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

public interface IncidentStatisticsReader {
    String PROCESS_KEYS = "processDefinitionKeys";
    AggregationBuilder COUNT_PROCESS_KEYS = terms(PROCESS_KEYS)
            .field(PROCESS_KEY)
            .size(ElasticsearchUtil.TERMS_AGG_SIZE);
    QueryBuilder INCIDENTS_QUERY =
            joinWithAnd(
                    termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION),
                    termQuery(STATE, ProcessInstanceState.ACTIVE.toString()),
                    termQuery(INCIDENT, true));

    Set<IncidentsByProcessGroupStatisticsDto> getProcessAndIncidentsStatistics();

    Set<IncidentsByErrorMsgStatisticsDto> getIncidentStatisticsByError();
}
