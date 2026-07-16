package com.cobloom.service.retrieval;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cobloom.entity.GrowthRecord;
import com.cobloom.entity.KnowledgeNode;
import com.cobloom.entity.KnowledgeRelation;
import com.cobloom.mapper.GrowthRecordMapper;
import com.cobloom.mapper.KnowledgeNodeMapper;
import com.cobloom.mapper.KnowledgeRelationMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class KeywordRetriever {
  private static final Logger log = LoggerFactory.getLogger(KeywordRetriever.class);
  private static final int TOP_K = 8;

  private final KnowledgeNodeMapper nodeMapper;
  private final KnowledgeRelationMapper relationMapper;
  private final GrowthRecordMapper recordMapper;

  public KeywordRetriever(KnowledgeNodeMapper nodeMapper, KnowledgeRelationMapper relationMapper,
                          GrowthRecordMapper recordMapper) {
    this.nodeMapper = nodeMapper;
    this.relationMapper = relationMapper;
    this.recordMapper = recordMapper;
  }

  public List<RetrievalCandidate> retrieve(Long userId, ExpandedQuery query) {
    Set<String> terms = queryTerms(query);
    if (terms.isEmpty()) return List.of();

    List<RetrievalCandidate> candidates = new ArrayList<>();
    for (KnowledgeNode node : nodeMapper.selectList(new QueryWrapper<KnowledgeNode>().eq("user_id", userId))) {
      RetrievalCandidate candidate = scoreNode(node, terms);
      if (candidate != null) candidates.add(candidate);
    }
    for (GrowthRecord record : recordMapper.selectList(new QueryWrapper<GrowthRecord>().eq("user_id", userId))) {
      RetrievalCandidate candidate = scoreRecord(record, terms);
      if (candidate != null) candidates.add(candidate);
    }

    return candidates.stream()
        .sorted((a, b) -> Double.compare(b.score(), a.score()))
        .limit(TOP_K)
        .toList();
  }

  private RetrievalCandidate scoreNode(KnowledgeNode node, Set<String> terms) {
    if (node == null) return null;
    double nameScore = matchScore(node.name, terms);
    double descriptionScore = matchScore(node.description, terms);
    double keywordScore = Math.min(1.0, nameScore * 0.7 + descriptionScore * 0.3);
    if (keywordScore <= 0) return null;

    Long recordId = sourceRecordId(node);
    if (recordId == null) return null;
    String content = nodeContent(node);
    logKeywordMatch("node", recordId, node.id, "name", matchedTerms(node.name, terms));
    logKeywordMatch("node", recordId, node.id, "description", matchedTerms(node.description, terms));
    return new RetrievalCandidate(
        "keyword_node",
        recordId,
        node.id,
        null,
        null,
        content,
        snippet(content),
        0,
        keywordScore,
        keywordScore * 0.85
    );
  }

  private RetrievalCandidate scoreRecord(GrowthRecord record, Set<String> terms) {
    if (record == null) return null;
    double titleScore = matchScore(record.title, terms);
    double keywordFieldScore = matchScore(record.keywords, terms);
    double keywordScore = Math.min(1.0, titleScore * 0.65 + keywordFieldScore * 0.35);
    if (keywordScore <= 0) return null;

    String content = recordContent(record);
    Long noteNodeId = noteNodeId(record.id);
    logKeywordMatch("record", record.id, noteNodeId, "title", matchedTerms(record.title, terms));
    logKeywordMatch("record", record.id, noteNodeId, "keywords", matchedTerms(record.keywords, terms));
    return new RetrievalCandidate(
        "keyword_record",
        record.id,
        noteNodeId,
        null,
        null,
        content,
        snippet(content),
        0,
        keywordScore,
        keywordScore * 0.8
    );
  }

  private double matchScore(String text, Set<String> terms) {
    String normalized = normalize(text);
    if (normalized.isBlank()) return 0;
    int hits = 0;
    for (String term : terms) {
      if (normalized.contains(term)) hits++;
    }
    return hits == 0 ? 0 : Math.min(1.0, hits / (double) Math.max(2, terms.size()));
  }

  private List<String> matchedTerms(String text, Set<String> terms) {
    String normalized = normalize(text);
    if (normalized.isBlank() || terms == null || terms.isEmpty()) return List.of();
    List<String> matches = new ArrayList<>();
    for (String term : terms) {
      if (normalized.contains(term)) matches.add(term);
    }
    return matches;
  }

  private void logKeywordMatch(String sourceType, Long recordId, Long nodeId, String field, List<String> matches) {
    if (matches == null || matches.isEmpty()) return;
    log.debug("RAG keyword match sourceType={}, recordId={}, nodeId={}, matchedField={}, matchedKeywords={}",
        sourceType,
        recordId,
        nodeId,
        field,
        matches);
  }

  private Set<String> queryTerms(ExpandedQuery query) {
    Set<String> terms = new LinkedHashSet<>();
    if (query == null) return terms;

    for (String term : query.allTerms()) {
      String normalized = normalize(term);
      if (normalized.length() >= 2 && !isStopWord(normalized)) terms.add(normalized);
      String compactTerm = normalized.replace(" ", "");
      if (compactTerm.length() >= 2 && !isStopWord(compactTerm)) terms.add(compactTerm);
    }

    for (String token : normalize(query.original()).split("\\s+")) {
      if (token.length() >= 2 && !isStopWord(token)) terms.add(token);
    }
    String compact = normalize(query.original()).replace(" ", "");
    if (compact.length() >= 2) {
      for (int i = 0; i < compact.length() - 1; i++) {
        String gram = compact.substring(i, Math.min(compact.length(), i + 2));
        if (!isStopWord(gram)) terms.add(gram);
      }
    }
    return terms;
  }

  private String nodeContent(KnowledgeNode node) {
    return "知识节点：" + safe(node.name)
        + "\n类型：" + safe(node.nodeType)
        + "\n说明：" + safe(node.description);
  }

  private Long sourceRecordId(KnowledgeNode node) {
    if (node.sourceRecordId != null) return node.sourceRecordId;
    KnowledgeRelation relation = relationMapper.selectOne(new QueryWrapper<KnowledgeRelation>()
        .eq("user_id", node.userId)
        .isNotNull("source_record_id")
        .and(w -> w.eq("source_node_id", node.id).or().eq("target_node_id", node.id))
        .last("limit 1"));
    return relation == null ? null : relation.sourceRecordId;
  }

  private String recordContent(GrowthRecord record) {
    return "笔记：" + safe(record.title)
        + "\n关键词：" + safe(record.keywords)
        + "\n摘要：" + safe(record.summary);
  }

  private Long noteNodeId(Long recordId) {
    if (recordId == null) return null;
    KnowledgeNode node = nodeMapper.selectOne(new QueryWrapper<KnowledgeNode>()
        .eq("node_type", "NOTE")
        .eq("source_record_id", recordId)
        .last("limit 1"));
    return node == null ? null : node.id;
  }

  private String snippet(String content) {
    String clean = safe(content).trim();
    return clean.length() <= 120 ? clean : clean.substring(0, 120);
  }

  private String normalize(String text) {
    return safe(text).toLowerCase(Locale.ROOT)
        .replaceAll("[\\p{Punct}，。！？；：、“”‘’（）【】《》]+", " ")
        .replaceAll("\\s+", " ")
        .trim();
  }

  private boolean isStopWord(String token) {
    return Arrays.asList("什么", "如何", "怎么", "以及", "这个", "那个", "我的", "我们", "the", "and", "for", "with")
        .contains(token);
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }
}
