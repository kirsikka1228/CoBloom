package com.cobloom.controller;

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class KnowledgeExecutorController {
  private final ThreadPoolTaskExecutor knowledgeTaskExecutor;

  public KnowledgeExecutorController(
      @Qualifier("knowledgeTaskExecutor") ThreadPoolTaskExecutor knowledgeTaskExecutor) {
    this.knowledgeTaskExecutor = knowledgeTaskExecutor;
  }

  @GetMapping("/api/internal/knowledge-executor")
  public Map<String, Object> knowledgeExecutor() {
    ThreadPoolExecutorStatus status = status();
    return Map.of(
        "corePoolSize", knowledgeTaskExecutor.getCorePoolSize(),
        "maxPoolSize", knowledgeTaskExecutor.getMaxPoolSize(),
        "queueCapacity", knowledgeTaskExecutor.getQueueCapacity(),
        "rejectionPolicy", "AbortPolicy",
        "activeCount", status.activeCount(),
        "poolSize", status.poolSize(),
        "queueSize", status.queueSize());
  }

  private ThreadPoolExecutorStatus status() {
    ThreadPoolExecutor executor = knowledgeTaskExecutor.getThreadPoolExecutor();
    return new ThreadPoolExecutorStatus(
        executor.getActiveCount(),
        executor.getPoolSize(),
        executor.getQueue().size());
  }

  private record ThreadPoolExecutorStatus(int activeCount, int poolSize, int queueSize) {
  }
}
