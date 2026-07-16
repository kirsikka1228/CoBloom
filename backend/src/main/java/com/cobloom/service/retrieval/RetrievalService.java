package com.cobloom.service.retrieval;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RetrievalService {
  private static final int TOP_K = 8;

  private final VectorRetriever vectorRetriever;
  private final KeywordRetriever keywordRetriever;
  private final GraphRetriever graphRetriever;
  private final QueryUnderstandingService queryUnderstandingService;
  private final RetrievalReranker retrievalReranker;

  public RetrievalService(VectorRetriever vectorRetriever, KeywordRetriever keywordRetriever, GraphRetriever graphRetriever,
                          QueryUnderstandingService queryUnderstandingService, RetrievalReranker retrievalReranker) {
    this.vectorRetriever = vectorRetriever;
    this.keywordRetriever = keywordRetriever;
    this.graphRetriever = graphRetriever;
    this.queryUnderstandingService = queryUnderstandingService;
    this.retrievalReranker = retrievalReranker;
  }

  public RetrievalResult retrieve(Long userId, String question) {
    ExpandedQuery expandedQuery = queryUnderstandingService.expand(question);
    Map<String, RetrievalCandidate> merged = new LinkedHashMap<>();
    for (RetrievalCandidate candidate : vectorRetriever.retrieve(userId, expandedQuery)) {
      merge(merged, candidate);
    }
    for (RetrievalCandidate candidate : keywordRetriever.retrieve(userId, expandedQuery)) {
      merge(merged, candidate);
    }
    for (RetrievalCandidate candidate : graphRetriever.retrieve(userId, merged.values().stream().toList())) {
      merge(merged, candidate);
    }

    List<RetrievalCandidate> candidates = retrievalReranker.rerank(expandedQuery, merged.values().stream().toList(), TOP_K);
    return new RetrievalResult(question, candidates);
  }

  private void merge(Map<String, RetrievalCandidate> merged, RetrievalCandidate candidate) {
    if (candidate == null) return;
    String key = candidate.chunkId() != null
        ? "chunk:" + candidate.chunkId()
        : candidate.knowledgeNodeId() != null
        ? "node:" + candidate.knowledgeNodeId()
        : "record:" + candidate.recordId() + ":" + candidate.content();
    RetrievalCandidate existing = merged.get(key);
    if (existing == null) {
      merged.put(key, candidate);
      return;
    }

    double vectorScore = Math.max(existing.vectorScore(), candidate.vectorScore());
    double keywordScore = Math.max(existing.keywordScore(), candidate.keywordScore());
    double score = Math.max(existing.score(), candidate.score()) + keywordScore * 0.15;
    merged.put(key, new RetrievalCandidate(
        existing.source() + "+" + candidate.source(),
        existing.recordId() != null ? existing.recordId() : candidate.recordId(),
        existing.knowledgeNodeId() != null ? existing.knowledgeNodeId() : candidate.knowledgeNodeId(),
        existing.chunkId() != null ? existing.chunkId() : candidate.chunkId(),
        existing.chunkIndex() != null ? existing.chunkIndex() : candidate.chunkIndex(),
        existing.content(),
        existing.snippet(),
        vectorScore,
        keywordScore,
        Math.min(1.0, score)
    ));
  }
}
