/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store.elasticsearch;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.FlowNodeStore;
import io.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static io.camunda.operate.schema.templates.ListViewTemplate.*;
import static io.camunda.operate.schema.templates.ListViewTemplate.ACTIVITY_ID;
import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ONLY_RUNTIME;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.operate.util.ElasticsearchUtil.scrollWith;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@Component
@Profile("!opensearch")
public class ElasticsearchFlowNodeStore implements FlowNodeStore {

  @Autowired
  private ListViewTemplate listViewTemplate;

  @Autowired
  private FlowNodeInstanceTemplate flowNodeInstanceTemplate;

  @Autowired
  private RestHighLevelClient esClient;

  @Override
  public String getFlowNodeIdByFlowNodeInstanceId(String flowNodeInstanceId) {
    final QueryBuilder query = joinWithAnd(termQuery(JOIN_RELATION, ACTIVITIES_JOIN_RELATION),
        termQuery(ListViewTemplate.ID, flowNodeInstanceId));
    final SearchRequest request = ElasticsearchUtil
        .createSearchRequest(listViewTemplate, ElasticsearchUtil.QueryType.ONLY_RUNTIME)
        .source(new SearchSourceBuilder()
            .query(query).fetchSource(ACTIVITY_ID, null));
    final SearchResponse response;
    try {
      response = esClient.search(request, RequestOptions.DEFAULT);
      if (response.getHits().getTotalHits().value != 1) {
        throw new OperateRuntimeException("Flow node instance is not found: " + flowNodeInstanceId);
      } else {
        return String.valueOf(response.getHits().getAt(0).getSourceAsMap().get(ACTIVITY_ID));
      }
    } catch (IOException e) {
      throw new OperateRuntimeException(
          "Error occurred when searching for flow node instance: " + flowNodeInstanceId, e);
    }
  }

  @Override
  public Map<String, String> getFlowNodeIdsForFlowNodeInstances(Set<String> flowNodeInstanceIds) {
    final Map<String, String> flowNodeIdsMap = new HashMap<>();
    final QueryBuilder q = termsQuery(FlowNodeInstanceTemplate.ID, flowNodeInstanceIds);
    final SearchRequest request = ElasticsearchUtil
        .createSearchRequest(flowNodeInstanceTemplate, ONLY_RUNTIME)
        .source(new SearchSourceBuilder().query(q)
            .fetchSource(
                new String[]{FlowNodeInstanceTemplate.ID, FlowNodeInstanceTemplate.FLOW_NODE_ID},
                null));
    try {
      scrollWith(request, esClient, searchHits -> {
        Arrays.stream(searchHits.getHits()).forEach(h -> flowNodeIdsMap.put(h.getId(),
            (String) h.getSourceAsMap().get(FlowNodeInstanceTemplate.FLOW_NODE_ID)));
      }, null, null);
    } catch (IOException e) {
      throw new OperateRuntimeException(
          "Exception occurred when searching for flow node ids: " + e.getMessage(), e);
    }
    return flowNodeIdsMap;
  }
}
