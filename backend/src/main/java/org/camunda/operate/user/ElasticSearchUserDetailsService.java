/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.user;

import java.util.Arrays;
import java.util.Collection;

import org.camunda.operate.entities.UserEntity;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.rest.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class ElasticSearchUserDetailsService implements UserDetailsService{

  private static final String ACT_USERNAME = "act", ACT_PASSWORD = ACT_USERNAME;

  private static final String ACT_ADMIN_ROLE = "ACTRADMIN";

  private static final String USER_ROLE = "USER";

  private static final Logger logger = LoggerFactory.getLogger(ElasticSearchUserDetailsService.class);

  @Autowired
  private UserStorage userStorage;
    
  @Autowired
  private OperateProperties operateProperties;
  
  @Autowired
  private PasswordEncoder passwordEncoder;
  
  public void initializeUsers() {
    String username = operateProperties.getUsername();
    if(!userExists(username)) {
      addUserWith(username, operateProperties.getPassword(), USER_ROLE);
    }   
    if(!userExists(ACT_USERNAME)) {
      addUserWith(ACT_USERNAME, ACT_PASSWORD, ACT_ADMIN_ROLE);
    }
  }

  protected ElasticSearchUserDetailsService addUserWith(String username,String password,String role) {
    logger.info("Create user in ElasticSearch for username {}",username);
    String passwordEncoded = passwordEncoder.encode(password);
    userStorage.create(UserEntity.from(username, passwordEncoded, role));
    return this;
  }
  
  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    try {
      UserEntity userEntity = userStorage.getByName(username);
      return new User(userEntity.getUsername(), userEntity.getPassword(),true,
          true, true,
          true, toAuthorities(userEntity.getRole()));
    }catch(NotFoundException e) {
      throw new UsernameNotFoundException(String.format("User with username '%s' not found.",username),e);
    }
  }

  protected Collection<? extends GrantedAuthority> toAuthorities(String role) {
    return Arrays.asList(new SimpleGrantedAuthority("ROLE_" + role));
  }
  
  protected boolean userExists(String username) {
    try {
      return userStorage.getByName(username)!=null;
    }catch(Throwable t) {
      return false;
    }
  }

}