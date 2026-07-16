package com.cobloom.service.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cobloom.entity.GrowthRecord;
import com.cobloom.entity.KnowledgeNode;
import com.cobloom.entity.KnowledgeRelation;
import com.cobloom.mapper.KnowledgeNodeMapper;
import com.cobloom.mapper.KnowledgeRelationMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StructuredKnowledgeService {
  private static final Logger log = LoggerFactory.getLogger(StructuredKnowledgeService.class);

  public static final String NODE_NOTE = "NOTE";
  public static final String NODE_CONCEPT = "CONCEPT";
  public static final String NODE_ENTITY = "ENTITY";

  private final KnowledgeNodeMapper nodeMapper;
  private final KnowledgeRelationMapper relationMapper;

  public StructuredKnowledgeService(KnowledgeNodeMapper nodeMapper, KnowledgeRelationMapper relationMapper) {
    this.nodeMapper = nodeMapper;
    this.relationMapper = relationMapper;
  }

  @Transactional
  public void persist(GrowthRecord record, KnowledgeCompileResult result) {
    log.info("StructuredKnowledgeService.persist start recordId={}, userId={}, title={}",
        record == null ? null : record.id,
        record == null ? null : record.userId,
        record == null ? null : record.title);
    if (record == null || record.id == null || result == null) {
      log.info("StructuredKnowledgeService.persist skipped reason=invalid_input recordId={}, userId={}, resultNull={}",
          record == null ? null : record.id,
          record == null ? null : record.userId,
          result == null);
      return;
    }
    removeRecordKnowledge(record.userId, record.id);

    KnowledgeNode noteNode = upsertNoteNode(record, result.summary());
    Map<String, KnowledgeNode> conceptNodes = upsertConceptNodes(record, result.concepts());
    Map<String, KnowledgeNode> entityNodes = upsertEntityNodes(record, result.entities());

    for (KnowledgeNode concept : conceptNodes.values()) {
      insertRelation(record.userId, noteNode.id, concept.id, RelationType.CONTAINS.value(), 1.0, 0.8,
          "Compiler extracted concept from note", record.id);
    }
    for (KnowledgeNode entity : entityNodes.values()) {
      insertRelation(record.userId, noteNode.id, entity.id, RelationType.CONTAINS.value(), 1.0, 0.8,
          "Compiler extracted entity from note", record.id);
    }

    for (KnowledgeRelationCandidate candidate : result.relations()) {
      KnowledgeNode source = resolveCandidateNode(candidate.source(), conceptNodes, entityNodes);
      KnowledgeNode target = resolveCandidateNode(candidate.target(), conceptNodes, entityNodes);
      if (source == null || target == null || source.id.equals(target.id)) continue;
      insertRelation(record.userId, source.id, target.id, normalizeRelationType(candidate.relationType(), source, target),
          0.7, null, candidate.evidence(), record.id);
    }
    Long nodeCount = nodeMapper.selectCount(new QueryWrapper<KnowledgeNode>()
        .eq("user_id", record.userId)
        .eq("source_record_id", record.id));
    Long relationCount = relationMapper.selectCount(new QueryWrapper<KnowledgeRelation>()
        .eq("user_id", record.userId)
        .eq("source_record_id", record.id));
    log.info("StructuredKnowledgeService.persist finished recordId={}, userId={}, noteNodeId={}, conceptCount={}, entityCount={}, sourceRecordNodeCount={}, sourceRecordRelationCount={}",
        record.id,
        record.userId,
        noteNode == null ? null : noteNode.id,
        conceptNodes.size(),
        entityNodes.size(),
        nodeCount,
        relationCount);
  }

  @Transactional
  public void removeRecordKnowledge(Long userId, Long recordId) {
    if (userId == null || recordId == null) return;
    relationMapper.delete(new QueryWrapper<KnowledgeRelation>()
        .eq("user_id", userId)
        .eq("source_record_id", recordId));
    KnowledgeNode note = nodeMapper.selectOne(new QueryWrapper<KnowledgeNode>()
        .eq("user_id", userId)
        .eq("node_type", NODE_NOTE)
        .eq("source_record_id", recordId));
    if (note != null) {
      relationMapper.delete(new QueryWrapper<KnowledgeRelation>()
          .eq("user_id", userId)
          .and(w -> w.eq("source_node_id", note.id).or().eq("target_node_id", note.id)));
      nodeMapper.deleteById(note.id);
    }
    removeOrphanSharedNodes(userId);
  }

  private void removeOrphanSharedNodes(Long userId) {
    List<KnowledgeNode> sharedNodes = nodeMapper.selectList(new QueryWrapper<KnowledgeNode>()
        .eq("user_id", userId)
        .in("node_type", NODE_CONCEPT, NODE_ENTITY));
    int removed = 0;
    for (KnowledgeNode node : sharedNodes) {
      Long relationCount = relationMapper.selectCount(new QueryWrapper<KnowledgeRelation>()
          .eq("user_id", userId)
          .and(w -> w.eq("source_node_id", node.id).or().eq("target_node_id", node.id)));
      if (relationCount == 0) {
        nodeMapper.deleteById(node.id);
        removed++;
      }
    }
    if (removed > 0) {
      log.info("StructuredKnowledgeService removed orphan shared nodes userId={}, removedCount={}",
          userId, removed);
    }
  }

  private KnowledgeNode upsertNoteNode(GrowthRecord record, String summary) {
    KnowledgeNode node = nodeMapper.selectOne(new QueryWrapper<KnowledgeNode>()
        .eq("user_id", record.userId)
        .eq("node_type", NODE_NOTE)
        .eq("source_record_id", record.id));
    LocalDateTime now = LocalDateTime.now();
    if (node == null) {
      node = new KnowledgeNode();
      node.userId = record.userId;
      node.nodeType = NODE_NOTE;
      node.name = record.title;
      node.normalizedName = normalizeName(record.title);
      node.description = summary;
      node.sourceRecordId = record.id;
      node.createdAt = now;
      node.updatedAt = now;
      nodeMapper.insert(node);
    } else {
      node.name = record.title;
      node.normalizedName = normalizeName(record.title);
      node.description = summary;
      node.updatedAt = now;
      nodeMapper.updateById(node);
    }
    return node;
  }

  private Map<String, KnowledgeNode> upsertConceptNodes(GrowthRecord record, List<KnowledgeConceptCandidate> concepts) {
    Map<String, KnowledgeNode> nodes = new LinkedHashMap<>();
    if (concepts == null) return nodes;
    for (KnowledgeConceptCandidate concept : concepts) {
      String name = concept == null || concept.name() == null ? "" : concept.name().trim();
      if (name.isBlank()) continue;
      KnowledgeNode node = upsertSharedNode(record.userId, NODE_CONCEPT, name, concept.description());
      nodes.put(node.normalizedName, node);
    }
    return nodes;
  }

  private Map<String, KnowledgeNode> upsertEntityNodes(GrowthRecord record, List<KnowledgeEntityCandidate> entities) {
    Map<String, KnowledgeNode> nodes = new LinkedHashMap<>();
    if (entities == null) return nodes;
    for (KnowledgeEntityCandidate entity : entities) {
      String name = entity == null || entity.name() == null ? "" : entity.name().trim();
      if (name.isBlank()) continue;
      String description = entity.description();
      if (entity.type() != null && !entity.type().isBlank()) {
        description = "[" + entity.type() + "] " + (description == null ? "" : description);
      }
      KnowledgeNode node = upsertSharedNode(record.userId, NODE_ENTITY, name, description);
      nodes.put(node.normalizedName, node);
    }
    return nodes;
  }

  private KnowledgeNode upsertSharedNode(Long userId, String nodeType, String name, String description) {
    String normalized = normalizeName(name);
    KnowledgeNode node = nodeMapper.selectOne(new QueryWrapper<KnowledgeNode>()
        .eq("user_id", userId)
        .eq("node_type", nodeType)
        .eq("normalized_name", normalized));
    LocalDateTime now = LocalDateTime.now();
    if (node == null) {
      node = new KnowledgeNode();
      node.userId = userId;
      node.nodeType = nodeType;
      node.name = name;
      node.normalizedName = normalized;
      node.description = description == null ? "" : description;
      node.createdAt = now;
      node.updatedAt = now;
      nodeMapper.insert(node);
    } else {
      node.name = name;
      if (description != null && !description.isBlank()) node.description = description;
      node.updatedAt = now;
      nodeMapper.updateById(node);
    }
    return node;
  }

  private KnowledgeNode resolveCandidateNode(String name, Map<String, KnowledgeNode> conceptNodes,
                                             Map<String, KnowledgeNode> entityNodes) {
    String normalized = normalizeName(name);
    KnowledgeNode concept = conceptNodes.get(normalized);
    return concept != null ? concept : entityNodes.get(normalized);
  }

  private void insertRelation(Long userId, Long sourceNodeId, Long targetNodeId, String relationType, Double weight,
                              Double confidence, String evidenceText, Long sourceRecordId) {
    KnowledgeRelation relation = new KnowledgeRelation();
    relation.userId = userId;
    relation.sourceNodeId = sourceNodeId;
    relation.targetNodeId = targetNodeId;
    relation.relationType = relationType;
    relation.weight = weight == null ? 0.5 : weight;
    relation.confidence = confidence == null ? 0.5 : confidence;
    relation.evidenceText = evidenceText == null ? "" : evidenceText;
    relation.sourceRecordId = sourceRecordId;
    relation.createdAt = LocalDateTime.now();
    relationMapper.insert(relation);
  }

  public String normalizeRelationType(String relationType, KnowledgeNode source, KnowledgeNode target) {
    return RelationType.normalize(
        relationType,
        source == null ? null : source.nodeType,
        target == null ? null : target.nodeType
    );
  }

  private String normalizeName(String name) {
    return name == null ? "" : name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
  }
}
