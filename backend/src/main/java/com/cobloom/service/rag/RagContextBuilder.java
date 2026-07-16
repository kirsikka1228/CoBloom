package com.cobloom.service.rag;

import com.cobloom.entity.GrowthRecord;
import com.cobloom.entity.KnowledgeNode;
import com.cobloom.mapper.GrowthRecordMapper;
import com.cobloom.mapper.KnowledgeNodeMapper;
import com.cobloom.service.retrieval.RetrievalCandidate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RagContextBuilder {
  private static final Logger log = LoggerFactory.getLogger(RagContextBuilder.class);
  private static final int MAX_CONTEXTS = 8;

  private final GrowthRecordMapper recordMapper;
  private final KnowledgeNodeMapper nodeMapper;

  public RagContextBuilder(GrowthRecordMapper recordMapper, KnowledgeNodeMapper nodeMapper) {
    this.recordMapper = recordMapper;
    this.nodeMapper = nodeMapper;
  }

  public List<RagContextItem> build(List<RetrievalCandidate> candidates) {
    if (candidates == null || candidates.isEmpty()) {
      logContexts(List.of());
      return List.of();
    }

    List<RagContextItem> contexts = new ArrayList<>();
    for (RetrievalCandidate candidate : candidates) {
      RagContextItem context = contextItem(candidate);
      if (context != null && !context.content().isBlank()) contexts.add(context);
      if (contexts.size() >= MAX_CONTEXTS) break;
    }
    logContexts(contexts);
    return contexts;
  }

  private RagContextItem contextItem(RetrievalCandidate candidate) {
    if (candidate == null) return null;
    if (candidate.chunkId() != null) {
      return chunkContext(candidate);
    }
    if (candidate.knowledgeNodeId() != null) {
      RagContextItem nodeContext = nodeContext(candidate);
      if (nodeContext != null) return nodeContext;
    }
    if (candidate.recordId() != null) {
      return recordContext(candidate);
    }
    return null;
  }

  private RagContextItem chunkContext(RetrievalCandidate candidate) {
    Long recordId = candidate.recordId();
    if (recordId == null) return null;

    String content = normalize(candidate.content());
    if (content.isBlank()) return null;

    String title = noteTitle(recordId);
    return item(candidate, recordId, candidate.chunkId(), candidate.knowledgeNodeId(), title, content);
  }

  private RagContextItem nodeContext(RetrievalCandidate candidate) {
    KnowledgeNode node = nodeMapper.selectById(candidate.knowledgeNodeId());
    if (node == null) return null;

    Long recordId = node.sourceRecordId != null ? node.sourceRecordId : candidate.recordId();
    if (recordId == null) return null;

    String title = noteTitle(recordId);
    String content = joinLines(
        line("Note title", title),
        line("Knowledge description", node.description)
    );
    if (content.isBlank()) return null;
    return item(candidate, recordId, candidate.chunkId(), candidate.knowledgeNodeId(), title, content);
  }

  private RagContextItem recordContext(RetrievalCandidate candidate) {
    GrowthRecord record = recordMapper.selectById(candidate.recordId());
    if (record == null) return null;

    String content = joinLines(
        line("Note title", record.title),
        line("Summary", record.summary)
    );
    if (content.isBlank()) return null;
    return item(candidate, record.id, candidate.chunkId(), candidate.knowledgeNodeId(), record.title, content);
  }

  private RagContextItem item(RetrievalCandidate candidate, Long recordId, Long chunkId, Long knowledgeNodeId,
                              String title, String content) {
    return new RagContextItem(
        recordId,
        chunkId,
        knowledgeNodeId,
        normalize(title),
        content,
        snippet(content),
        normalize(candidate.source()),
        candidate.score()
    );
  }

  private String noteTitle(Long recordId) {
    if (recordId == null) return "";
    GrowthRecord record = recordMapper.selectById(recordId);
    return record == null ? "" : record.title;
  }

  private String line(String label, String value) {
    String clean = normalize(value);
    return clean.isBlank() ? "" : label + ": " + clean;
  }

  private String joinLines(String... lines) {
    List<String> cleanLines = new ArrayList<>();
    for (String line : lines) {
      if (line != null && !line.isBlank()) cleanLines.add(line);
    }
    return String.join("\n", cleanLines);
  }

  private String normalize(String value) {
    if (value == null) return "";
    return value
        .replaceAll("\\s+", " ")
        .trim();
  }

  private String snippet(String content) {
    String clean = normalize(content);
    return clean.length() <= 120 ? clean : clean.substring(0, 120);
  }

  private void logContexts(List<RagContextItem> contexts) {
    if (!log.isDebugEnabled()) return;
    log.debug("RAG context selected count={}", contexts == null ? 0 : contexts.size());
    if (contexts == null) return;
    for (int i = 0; i < contexts.size(); i++) {
      RagContextItem context = contexts.get(i);
      log.debug("RAG context item index={}, recordId={}, chunkId={}, knowledgeNodeId={}, source={}, score={}, title={}, snippet={}",
          i,
          context.recordId(),
          context.chunkId(),
          context.knowledgeNodeId(),
          context.sourceType(),
          round(context.score()),
          context.title(),
          context.snippet());
    }
  }

  private double round(double value) {
    return Math.round(value * 1000.0) / 1000.0;
  }
}
