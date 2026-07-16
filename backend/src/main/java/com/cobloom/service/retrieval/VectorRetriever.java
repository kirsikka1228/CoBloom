package com.cobloom.service.retrieval;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cobloom.entity.GrowthRecord;
import com.cobloom.entity.RecordChunk;
import com.cobloom.mapper.GrowthRecordMapper;
import com.cobloom.mapper.RecordChunkMapper;
import com.cobloom.service.rag.EmbeddingService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class VectorRetriever {
  private static final Logger log = LoggerFactory.getLogger(VectorRetriever.class);
  private static final int TOP_K = 8;
  private static final double COSINE_THRESHOLD = 0.2;

  private final RecordChunkMapper chunkMapper;
  private final GrowthRecordMapper recordMapper;
  private final EmbeddingService embeddingService;

  public VectorRetriever(RecordChunkMapper chunkMapper, GrowthRecordMapper recordMapper,
                         EmbeddingService embeddingService) {
    this.chunkMapper = chunkMapper;
    this.recordMapper = recordMapper;
    this.embeddingService = embeddingService;
  }

  public List<RetrievalCandidate> retrieve(Long userId, ExpandedQuery query) {
    String embeddingText = query == null ? "" : query.expandedText();
    double[] questionEmbedding = embeddingService.embed(embeddingText);
    List<RetrievalCandidate> candidates = new ArrayList<>();
    for (RecordChunk chunk : chunkMapper.selectList(new QueryWrapper<RecordChunk>().eq("user_id", userId))) {
      RetrievalCandidate candidate = scoreChunk(chunk, questionEmbedding);
      if (candidate != null) {
        log.debug("RAG vector candidate recordId={}, chunkId={}, chunkIndex={}, similarity={}, score={}",
            candidate.recordId(),
            candidate.chunkId(),
            candidate.chunkIndex(),
            round(candidate.vectorScore()),
            round(candidate.score()));
        candidates.add(candidate);
      }
    }
    return candidates.stream()
        .sorted(Comparator.comparingDouble(RetrievalCandidate::score).reversed())
        .limit(TOP_K)
        .toList();
  }

  private RetrievalCandidate scoreChunk(RecordChunk chunk, double[] questionEmbedding) {
    if (chunk == null || chunk.content == null || chunk.content.isBlank()) return null;
    if (chunk.embedding == null || chunk.embedding.isBlank()) return null;

    double cosine = embeddingService.cosine(questionEmbedding, chunk.embedding);
    if (!Double.isFinite(cosine) || cosine < COSINE_THRESHOLD) return null;

    GrowthRecord record = recordMapper.selectById(chunk.recordId);
    LocalDateTime updateTime = record == null ? chunk.createdAt : record.updatedAt;
    double score = computeScore(cosine, chunk.content, updateTime);
    if (!Double.isFinite(score)) return null;
    return new RetrievalCandidate(
        "vector",
        chunk.recordId,
        null,
        chunk.id,
        chunk.chunkIndex,
        chunk.content,
        snippet(chunk.content),
        cosine,
        0,
        score
    );
  }

  private double computeScore(double cosine, String chunk, LocalDateTime updateTime) {
    int length = chunk == null ? 0 : chunk.length();
    double chunkQuality;
    if (length >= 200 && length <= 800) {
      chunkQuality = 1.0;
    } else if (length < 200) {
      chunkQuality = 0.6;
    } else {
      chunkQuality = 0.7;
    }

    double recency = 0.5;
    if (updateTime != null) {
      long days = Math.max(0, Duration.between(updateTime, LocalDateTime.now()).toDays());
      recency = Math.max(0.0, 1.0 - days / 365.0);
    }

    double score = cosine * 0.7 + chunkQuality * 0.2 + recency * 0.1;
    return Double.isFinite(score) ? score : 0;
  }

  private String snippet(String chunk) {
    if (chunk == null) return "";
    String clean = chunk.trim();
    return clean.length() <= 120 ? clean : clean.substring(0, 120);
  }

  private double round(double value) {
    return Math.round(value * 1000.0) / 1000.0;
  }
}
