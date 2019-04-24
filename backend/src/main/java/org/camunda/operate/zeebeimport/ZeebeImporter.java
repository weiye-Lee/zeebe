/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import java.io.IOException;
import javax.annotation.PreDestroy;
import org.camunda.operate.property.OperateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
@Configuration
public class ZeebeImporter extends Thread  {

  private static final Logger logger = LoggerFactory.getLogger(ZeebeImporter.class);

  private boolean shutdown = false;

  /**
   * Lock object, that can be used to be informed about finished import.
   */
  private final Object importFinished = new Object();

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private RecordsReaderHolder recordsReaderHolder;

  @Autowired
  private BeanFactory beanFactory;

  public void startImportingData() {
    if (operateProperties.isStartLoadingDataOnStartup()) {
      start();
    }
  }

  @Override
  public void run() {
    logger.debug("Start importing data");
    while (!shutdown) {
      synchronized (importFinished) {
        try {
          int countRecords = performOneRoundOfImport();
          if (countRecords == 0) {
            importFinished.notifyAll();
            doBackoff();
          }
        } catch (Exception ex) {
          //retry
          logger.error("Error occurred while importing Zeebe data. Will be retried.", ex);
          doBackoff();
        }
      }
    }
  }

  public int performOneRoundOfImport() throws IOException {
    int countRecords = 0;
    for (RecordsReader recordsReader: recordsReaderHolder.getActiveRecordsReaders()) {
      countRecords += importOneBatch(recordsReader);
    }
    return countRecords;
  }

  public int importOneBatch(RecordsReader recordsReader) throws IOException {
    return recordsReader.readAndScheduleNextBatch();
  }

  @Bean("importThreadPoolExecutor")
  public ThreadPoolTaskExecutor getTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(operateProperties.getImportProperties().getThreadsCount());
    executor.setMaxPoolSize(operateProperties.getImportProperties().getThreadsCount());
    executor.setThreadNamePrefix("import_thread_");
    executor.initialize();
    return executor;
  }

  public Object getImportFinished() {
    return importFinished;
  }

  @PreDestroy
  public void shutdown() {
    shutdown = true;
    synchronized (importFinished) {
      importFinished.notifyAll();
    }
  }

  private void doBackoff() {
    try {
      Thread.sleep(2000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

}
