package com.cobloom.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobloom.service.ai.MockAiProvider;
import com.cobloom.service.rag.MockEmbeddingService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ConcurrencySafetyTest {

  @Test
  void concurrentAiRequestsRemainDeterministicAndDoNotLeakState() throws Exception {
    MockAiProvider provider = new MockAiProvider();
    int workers = 20;
    CountDownLatch ready = new CountDownLatch(workers);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(workers);
    try {
      List<Future<String>> futures = new ArrayList<>();
      for (int i = 0; i < workers; i++) {
        futures.add(pool.submit(() -> {
          ready.countDown();
          assertTrue(start.await(2, TimeUnit.SECONDS));
          return provider.complete("", "TASK:knowledge-keywords\nRAG retrieval embedding");
        }));
      }
      assertTrue(ready.await(2, TimeUnit.SECONDS));
      start.countDown();
      String expected = futures.getFirst().get(2, TimeUnit.SECONDS);
      for (Future<String> future : futures) assertEquals(expected, future.get(2, TimeUnit.SECONDS));
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void concurrentEmbeddingRequestsAreStableOnSharedServiceInstance() throws Exception {
    MockEmbeddingService embedding = new MockEmbeddingService();
    ExecutorService pool = Executors.newFixedThreadPool(8);
    try {
      Callable<double[]> task = () -> embedding.embed("same knowledge text");
      List<Future<double[]>> futures = pool.invokeAll(java.util.Collections.nCopies(32, task));
      double[] expected = futures.getFirst().get();
      for (Future<double[]> future : futures) {
        double[] actual = future.get();
        assertEquals(expected.length, actual.length);
        assertEquals(1.0, embedding.cosine(expected, actual), 0.000001);
      }
    } finally {
      pool.shutdownNow();
    }
  }
}
