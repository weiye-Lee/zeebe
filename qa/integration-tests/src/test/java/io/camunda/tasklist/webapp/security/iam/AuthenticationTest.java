/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp.security.iam;

import static io.camunda.tasklist.webapp.security.TasklistURIs.COOKIE_JSESSIONID;
import static io.camunda.tasklist.webapp.security.TasklistURIs.IAM_AUTH_PROFILE;
import static io.camunda.tasklist.webapp.security.TasklistURIs.IAM_CALLBACK_URI;
import static io.camunda.tasklist.webapp.security.TasklistURIs.LOGIN_RESOURCE;
import static io.camunda.tasklist.webapp.security.TasklistURIs.NO_PERMISSION;
import static io.camunda.tasklist.webapp.security.TasklistURIs.ROOT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.auth0.jwt.exceptions.TokenExpiredException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.tasklist.webapp.security.AuthenticationTestable;
import io.camunda.tasklist.webapp.security.Permission;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.tasklist.webapp.security.oauth.OAuth2WebConfigurer;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {
      TestApplicationWithNoBeans.class,
      IAMWebSecurityConfig.class,
      IAMController.class,
      IAMAuthentication.class,
      TasklistURIs.class,
      TasklistProperties.class,
      OAuth2WebConfigurer.class,
    },
    properties = {
      "camunda.tasklist.iam.issuer=http://app.iam.localhost",
      "camunda.tasklist.iam.issuerUrl=http://app.iam.localhost",
      "camunda.tasklist.iam.clientId=tasklist",
      "camunda.tasklist.iam.clientSecret=123",
      "server.servlet.session.cookie.name = " + COOKIE_JSESSIONID
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({IAM_AUTH_PROFILE, "test"})
public class AuthenticationTest implements AuthenticationTestable {

  @LocalServerPort private int randomServerPort;

  @Autowired private TestRestTemplate testRestTemplate;

  @MockBean private IAMAuthentication iamAuthentication;

  @Autowired private TasklistProperties tasklistProperties;

  @Test
  public void testAccessNoPermission() {
    final ResponseEntity<String> response = get(NO_PERMISSION);
    assertThat(response.getBody()).contains("No permission for Tasklist");
  }

  @Test
  public void testLoginSuccess() {
    // Step 1 try to access document root
    ResponseEntity<String> response = get(ROOT);
    final HttpEntity<?> cookies = httpEntityWithCookie(response);

    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));

    // Step 2 Get Login provider url
    response = get(LOGIN_RESOURCE, cookies);
    assertThat(redirectLocationIn(response))
        .contains(
            tasklistProperties.getIam().getIssuerUrl(),
            URLEncoder.encode(IAM_CALLBACK_URI, Charset.defaultCharset()),
            tasklistProperties.getIam().getClientId());
    // Step 3 assume authentication will be successful
    when(iamAuthentication.isAuthenticated()).thenReturn(true);
    // Step 4 do callback
    response = get(IAM_CALLBACK_URI, cookies);
    assertThatRequestIsRedirectedTo(response, urlFor(ROOT));
    // Step 5  check if access to url possible
    response = get(ROOT, cookies);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("<body>");
  }

  @Test
  public void testLoginFailedWithNoPermissions() throws Exception {
    // Step 1 try to access document root
    ResponseEntity<String> response = get(ROOT);
    final HttpEntity<?> cookies = httpEntityWithCookie(response);

    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));

    // Step 2 Get Login provider url
    response = get(LOGIN_RESOURCE, cookies);
    assertThat(redirectLocationIn(response))
        .contains(
            tasklistProperties.getIam().getIssuerUrl(),
            URLEncoder.encode(IAM_CALLBACK_URI, Charset.defaultCharset()),
            tasklistProperties.getIam().getClientId());
    // Step 3 assume authentication will be fail
    doThrow(TokenExpiredException.class).when(iamAuthentication).authenticate(any(), any());
    when(iamAuthentication.isAuthenticated()).thenReturn(false);
    response = get(IAM_CALLBACK_URI, cookies);

    assertThat(redirectLocationIn(response)).contains(NO_PERMISSION);

    response = get(ROOT, cookies);
    // Check that access to url is not possible
    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));
  }

  @Test
  public void testLoginFailedWithNoReadPermissions() throws Exception {
    // Step 1 try to access document root
    ResponseEntity<String> response = get(ROOT);
    final HttpEntity<?> cookies = httpEntityWithCookie(response);

    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));

    // Step 2 Get Login provider url
    response = get(LOGIN_RESOURCE, cookies);
    assertThat(redirectLocationIn(response))
        .contains(
            tasklistProperties.getIam().getIssuerUrl(),
            URLEncoder.encode(IAM_CALLBACK_URI, Charset.defaultCharset()),
            tasklistProperties.getIam().getClientId());
    // Step 3 assume authentication succeed but return no READ permission
    doThrow(TokenExpiredException.class).when(iamAuthentication).authenticate(any(), any());
    when(iamAuthentication.isAuthenticated()).thenReturn(true);
    when(iamAuthentication.getPermissions()).thenReturn(List.of(Permission.WRITE));
    response = get(IAM_CALLBACK_URI, cookies);

    assertThat(redirectLocationIn(response)).contains(NO_PERMISSION);

    response = get(ROOT, cookies);
    // Check that access to url is not possible
    assertThatRequestIsRedirectedTo(response, urlFor(LOGIN_RESOURCE));
  }

  @Override
  public TestRestTemplate getTestRestTemplate() {
    return testRestTemplate;
  }

  private HttpEntity<?> httpEntityWithCookie(ResponseEntity<String> response) {
    final HttpHeaders headers = new HttpHeaders();
    headers.add("Cookie", getCookiesAsString(response.getHeaders()));
    return new HttpEntity<>(new HashMap<>(), headers);
  }

  protected void assertThatRequestIsRedirectedTo(ResponseEntity<?> response, String url) {
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    assertThat(redirectLocationIn(response)).isEqualTo(url);
  }

  private String redirectLocationIn(ResponseEntity<?> response) {
    return response.getHeaders().getLocation().toString();
  }

  private String urlFor(String path) {
    return "http://localhost:" + randomServerPort + path;
  }

  private ResponseEntity<String> get(String path, HttpEntity<?> requestEntity) {
    return testRestTemplate.exchange(path, HttpMethod.GET, requestEntity, String.class);
  }
}
