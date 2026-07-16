package com.cobloom.service.retrieval;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cobloom.entity.KnowledgeNode;
import com.cobloom.entity.KnowledgeRelation;
import com.cobloom.mapper.KnowledgeNodeMapper;
import com.cobloom.mapper.KnowledgeRelationMapper;
import com.cobloom.service.knowledge.RelationType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GraphRetriever {
  private static final Logger log = LoggerFactory.getLogger(GraphRetriever.class);
  private static final int MAX_SEED_CANDIDATES = 5;
  private static final int MAX_EXPANDED_CANDIDATES = 6;
  private static final Set<String> ALLOWED_RELATIONS = Set.of(
      RelationType.CONTAINS.value(),
      RelationType.RELATED_TO.value(),
      RelationType.PREREQUISITE.value(),
      RelationType.PART_OF.value()
  );

  private final KnowledgeNodeMapper nodeMapper;
  private final KnowledgeRelationMapper relationMapper;

  public GraphRetriever(KnowledgeNodeMapper nodeMapper, KnowledgeRelationMapper relationMapper) {
    this.nodeMapper = nodeMapper;
    this.relationMapper = relationMapper;
  }

  public List<RetrievalCandidate> retrieve(Long userId, List<RetrievalCandidate> initialCandidates) {
    if (initialCandidates == null || initialCandidates.isEmpty()) return List.of();

    List<Long> seedNodeIds = initialCandidates.stream()
        .sorted(Comparator.comparingDouble(RetrievalCandidate::score).reversed())
        .limit(MAX_SEED_CANDIDATES)
        .flatMap(candidate -> seedNodeIds(userId, candidate).stream())
        .distinct()
        .toList();
    if (seedNodeIds.isEmpty()) return List.of();
    log.debug("RAG graph seeds userId={}, seedNodes={}", userId, nodeLabels(seedNodeIds));

    Set<Long> seenNodeIds = new HashSet<>(seedNodeIds);
    List<GraphExpansion> expansions = new ArrayList<>();
    for (Long seedNodeId : seedNodeIds) {
      for (KnowledgeRelation relation : relationsFor(userId, seedNodeId)) {
        if (!isAllowed(relation)) continue;
        Long neighborId = relation.sourceNodeId.equals(seedNodeId) ? relation.targetNodeId : relation.sourceNodeId;
        if (!seenNodeIds.add(neighborId)) continue;
        KnowledgeNode neighbor = nodeMapper.selectById(neighborId);
        if (neighbor == null || !userId.equals(neighbor.userId)) continue;
        Long recordId = sourceRecordId(neighbor, relation);
        if (recordId == null) continue;
        log.debug("RAG graph expansion seedNode={}, expandedNode={}, relation={}, weight={}, evidencePresent={}, recordId={}",
            nodeLabel(seedNodeId),
            nodeLabel(neighbor),
            relation.relationType,
            relation.weight,
            relation.evidenceText != null && !relation.evidenceText.isBlank(),
            recordId);
        expansions.add(new GraphExpansion(neighbor, relation, recordId));
      }
    }

    return expansions.stream()
        .sorted(Comparator.comparingDouble(GraphExpansion::rankingScore).reversed())
        .limit(MAX_EXPANDED_CANDIDATES)
        .map(this::candidate)
        .toList();
  }

  private List<Long> seedNodeIds(Long userId, RetrievalCandidate candidate) {
    if (candidate.knowledgeNodeId() != null) return List.of(candidate.knowledgeNodeId());
    if (candidate.recordId() == null) return List.of();
    KnowledgeNode note = nodeMapper.selectOne(new QueryWrapper<KnowledgeNode>()
        .eq("user_id", userId)
        .eq("node_type", "NOTE")
        .eq("source_record_id", candidate.recordId())
        .last("limit 1"));
    return note == null ? List.of() : List.of(note.id);
  }

  private List<KnowledgeRelation> relationsFor(Long userId, Long nodeId) {
    return relationMapper.selectList(new QueryWrapper<KnowledgeRelation>()
        .eq("user_id", userId)
        .and(w -> w.eq("source_node_id", nodeId).or().eq("target_node_id", nodeId)));
  }

  private boolean isAllowed(KnowledgeRelation relation) {
    if (relation == null || relation.relationType == null) return false;
    if (!ALLOWED_RELATIONS.contains(relation.relationType)) return false;
    if (relation.sourceNodeId == null || relation.targetNodeId == null
        || relation.sourceNodeId.equals(relation.targetNodeId)) return false;
    return relation.evidenceText != null && !relation.evidenceText.isBlank();
  }

  private Long sourceRecordId(KnowledgeNode node, KnowledgeRelation relation) {
    if (node.sourceRecordId != null) return node.sourceRecordId;
    return relation.sourceRecordId;
  }

  private RetrievalCandidate candidate(GraphExpansion expansion) {
    KnowledgeNode node = expansion.node;
    KnowledgeRelation relation = expansion.relation;
    double graphScore = Math.min(0.78, 0.45 + normalizedWeight(relation) * 0.25
        + relationTypeBoost(relation.relationType));
    String content = "图谱扩展节点：" + safe(node.name)
        + "\n类型：" + safe(node.nodeType)
        + "\n关系：" + safe(relation.relationType)
        + "\n说明：" + safe(node.description)
        + "\n证据：" + safe(relation.evidenceText);
    return new RetrievalCandidate(
        "graph",
        expansion.recordId,
        node.id,
        null,
        null,
        content,
        snippet(content),
        0,
        0,
        graphScore
    );
  }

  private String snippet(String content) {
    String clean = safe(content).trim();
    return clean.length() <= 120 ? clean : clean.substring(0, 120);
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }

  private static double normalizedWeight(KnowledgeRelation relation) {
    double weight = relation == null || relation.weight == null ? 0.7 : relation.weight;
    return Math.max(0, Math.min(1, weight));
  }

  private static double relationTypeBoost(String relationType) {
    if (RelationType.PREREQUISITE.value().equals(relationType)
        || RelationType.PART_OF.value().equals(relationType)) return 0.08;
    if (RelationType.CONTAINS.value().equals(relationType)) return 0.04;
    return 0;
  }

  private List<String> nodeLabels(List<Long> nodeIds) {
    if (nodeIds == null || nodeIds.isEmpty()) return List.of();
    return nodeIds.stream().map(this::nodeLabel).toList();
  }

  private String nodeLabel(Long nodeId) {
    if (nodeId == null) return "";
    KnowledgeNode node = nodeMapper.selectById(nodeId);
    return node == null ? "node:" + nodeId : nodeLabel(node);
  }

  private String nodeLabel(KnowledgeNode node) {
    if (node == null) return "";
    return "node:" + node.id + ":" + safe(node.name);
  }

  private record GraphExpansion(KnowledgeNode node, KnowledgeRelation relation, Long recordId) {
    double rankingScore() {
      return normalizedWeight(relation) + relationTypeBoost(relation.relationType);
    }
  }
}
