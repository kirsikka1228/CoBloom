package com.cobloom.service.retrieval;

import com.cobloom.entity.GrowthRecord;
import com.cobloom.entity.KnowledgeNode;
import com.cobloom.mapper.GrowthRecordMapper;
import com.cobloom.mapper.KnowledgeNodeMapper;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RetrievalReranker {
  private static final Logger log = LoggerFactory.getLogger(RetrievalReranker.class);
  private static final double MIN_FINAL_SCORE = 0.35;
  private static final double MIN_GRAPH_SCORE = 0.45;

  private final GrowthRecordMapper recordMapper;
  private final KnowledgeNodeMapper nodeMapper;

  public RetrievalReranker(GrowthRecordMapper recordMapper, KnowledgeNodeMapper nodeMapper) {
    this.recordMapper = recordMapper;
    this.nodeMapper = nodeMapper;
  }

  public List<RetrievalCandidate> rerank(ExpandedQuery query, List<RetrievalCandidate> candidates, int limit) {
    if (query == null || candidates == null || candidates.isEmpty()) return List.of();
    return candidates.stream()
        .map(candidate -> rerank(query, candidate))
        .filter(Objects::nonNull)
        .filter(candidate -> candidate.score() >= threshold(candidate))
        .sorted(Comparator.comparingDouble(RetrievalCandidate::score).reversed())
        .limit(limit)
        .toList();
  }

  private RetrievalCandidate rerank(ExpandedQuery query, RetrievalCandidate candidate) {
    if (candidate == null) return null;

    GrowthRecord record = candidate.recordId() == null ? null : recordMapper.selectById(candidate.recordId());
    KnowledgeNode node = candidate.knowledgeNodeId() == null ? null : nodeMapper.selectById(candidate.knowledgeNodeId());

    String title = record == null ? "" : record.title;
    String keywords = record == null ? "" : record.keywords;
    String summary = record == null ? "" : record.summary;
    String nodeText = node == null ? "" : join(node.name, node.normalizedName, node.description);
    String content = join(candidate.content(), candidate.snippet(), summary, keywords);

    double titleMatch = matchScore(query.allTerms(), title);
    double keywordMatch = matchScore(query.allTerms(), keywords);
    double nodeMatch = matchScore(query.allTerms(), nodeText);
    double contentMatch = matchScore(query.allTerms(), content);
    double exactAcronymMatch = exactAcronymMatch(query, join(title, keywords, nodeText, content));

    double finalScore =
        candidate.vectorScore() * 0.35
            + candidate.keywordScore() * 0.25
            + titleMatch * 0.20
            + nodeMatch * 0.10
            + Math.max(keywordMatch, contentMatch) * 0.10
            + exactAcronymMatch * 0.20;

    finalScore = Math.max(finalScore, candidate.score() * 0.45 + exactAcronymMatch * 0.25 + titleMatch * 0.20);
    PenaltyDetails penalty = topicMismatchPenaltyDetails(query, title, keywords, content);
    finalScore = Math.max(0, Math.min(1.0, finalScore - penalty.value()));
    log.debug("RAG rerank candidate source={}, recordId={}, nodeId={}, chunkId={}, vectorScore={}, keywordScore={}, finalScore={}, threshold={}, penalty={}, penaltyReasons={}",
        candidate.source(),
        candidate.recordId(),
        candidate.knowledgeNodeId(),
        candidate.chunkId(),
        round(candidate.vectorScore()),
        round(candidate.keywordScore()),
        round(finalScore),
        round(threshold(candidate)),
        round(penalty.value()),
        penalty.reasons());

    return new RetrievalCandidate(
        candidate.source(),
        candidate.recordId(),
        candidate.knowledgeNodeId(),
        candidate.chunkId(),
        candidate.chunkIndex(),
        candidate.content(),
        candidate.snippet(),
        candidate.vectorScore(),
        candidate.keywordScore(),
        finalScore
    );
  }

  private double threshold(RetrievalCandidate candidate) {
    if (candidate == null) return 1.0;
    return safe(candidate.source()).contains("graph") ? MIN_GRAPH_SCORE : MIN_FINAL_SCORE;
  }

  private double matchScore(List<String> terms, String text) {
    String normalized = normalize(text);
    if (terms == null || terms.isEmpty() || normalized.isBlank()) return 0;

    int hits = 0;
    int weightedHits = 0;
    for (String term : terms) {
      String normalizedTerm = normalize(term);
      if (normalizedTerm.length() < 2) continue;
      if (normalized.contains(normalizedTerm)) {
        hits++;
        weightedHits += normalizedTerm.length() >= 3 ? 2 : 1;
      }
    }
    if (hits == 0) return 0;
    return Math.min(1.0, weightedHits / 6.0);
  }

  private double exactAcronymMatch(ExpandedQuery query, String text) {
    String haystack = normalize(text);
    double score = 0;
    for (String term : query.terms()) {
      String raw = safe(term).trim();
      if (raw.matches("[A-Za-z][A-Za-z0-9]{1,}")) {
        String normalized = normalize(raw);
        if (haystack.contains(normalized)) score = Math.max(score, 1.0);
      }
    }
    return score;
  }

  private PenaltyDetails topicMismatchPenaltyDetails(ExpandedQuery query, String title, String keywords, String content) {
    String text = normalize(join(title, keywords, content));
    String original = normalize(query.original());
    double penalty = 0;
    List<String> reasons = new java.util.ArrayList<>();

    if (original.contains("stm32") && text.contains("esp32") && !text.contains("stm32")) {
      penalty += 0.35;
      reasons.add("query_stm32_candidate_esp32");
    }
    if (original.contains("esp32") && text.contains("stm32") && !text.contains("esp32")) {
      penalty += 0.35;
      reasons.add("query_esp32_candidate_stm32");
    }
    if (!hasCoreHit(query, text)) {
      penalty += 0.20;
      reasons.add("missing_core_term_or_alias");
    }
    return new PenaltyDetails(penalty, reasons);
  }

  private boolean hasCoreHit(ExpandedQuery query, String normalizedText) {
    for (String term : query.terms()) {
      String normalized = normalize(term);
      if (normalized.length() >= 2 && normalizedText.contains(normalized)) return true;
    }
    for (String alias : query.aliases()) {
      String normalized = normalize(alias);
      if (normalized.length() >= 3 && normalizedText.contains(normalized)) return true;
    }
    return false;
  }

  private String join(String... values) {
    return String.join(" ", java.util.Arrays.stream(values)
        .filter(value -> value != null && !value.isBlank())
        .toList());
  }

  private String normalize(String text) {
    return safe(text).toLowerCase(Locale.ROOT)
        .replaceAll("[\\p{Punct}，。！？；：、“”‘’（）【】《》]+", " ")
        .replaceAll("\\s+", " ")
        .trim();
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }

  private double round(double value) {
    return Math.round(value * 1000.0) / 1000.0;
  }

  private record PenaltyDetails(double value, List<String> reasons) {}
}
