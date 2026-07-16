package com.cobloom.service.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cobloom.dto.GraphEdgeDTO;
import com.cobloom.dto.GraphNodeDTO;
import com.cobloom.dto.KnowledgeGraphDTO;
import com.cobloom.dto.KnowledgeNodeDetailDTO;
import com.cobloom.entity.GrowthRecord;
import com.cobloom.entity.KnowledgeNode;
import com.cobloom.entity.KnowledgeRelation;
import com.cobloom.mapper.GrowthRecordMapper;
import com.cobloom.mapper.KnowledgeNodeMapper;
import com.cobloom.mapper.KnowledgeRelationMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeGraphQueryService {
  private static final int DEFAULT_DEPTH = 1;
  private static final int MAX_DEPTH = 3;

  private final KnowledgeNodeMapper nodeMapper;
  private final KnowledgeRelationMapper relationMapper;
  private final GrowthRecordMapper recordMapper;

  public KnowledgeGraphQueryService(KnowledgeNodeMapper nodeMapper, KnowledgeRelationMapper relationMapper,
                                    GrowthRecordMapper recordMapper) {
    this.nodeMapper = nodeMapper;
    this.relationMapper = relationMapper;
    this.recordMapper = recordMapper;
  }

  public KnowledgeGraphDTO graph(Long userId) {
    List<KnowledgeRelation> relations = relationMapper.selectList(new QueryWrapper<KnowledgeRelation>()
        .eq("user_id", userId));
    Set<Long> connectedNodeIds = new LinkedHashSet<>();
    for (KnowledgeRelation relation : relations) {
      connectedNodeIds.add(relation.sourceNodeId);
      connectedNodeIds.add(relation.targetNodeId);
    }

    List<KnowledgeNode> nodes = nodeMapper.selectList(new QueryWrapper<KnowledgeNode>()
        .eq("user_id", userId)
        .orderByAsc("node_type")
        .orderByAsc("name"));
    Map<Long, KnowledgeNode> nodeById = new LinkedHashMap<>();
    for (KnowledgeNode node : nodes) {
      if (connectedNodeIds.contains(node.id)) nodeById.put(node.id, node);
    }
    return graphFrom(userId, nodeById, relations);
  }

  public KnowledgeGraphDTO noteGraph(Long userId, Long noteId) {
    GrowthRecord note = recordMapper.selectOne(new QueryWrapper<GrowthRecord>()
        .eq("id", noteId)
        .eq("user_id", userId));
    if (note == null) throw new IllegalArgumentException("笔记不存在或无权访问");

    KnowledgeNode noteNode = nodeMapper.selectOne(new QueryWrapper<KnowledgeNode>()
        .eq("user_id", userId)
        .eq("node_type", StructuredKnowledgeService.NODE_NOTE)
        .eq("source_record_id", noteId));
    if (noteNode == null) return new KnowledgeGraphDTO(List.of(), List.of());

    List<KnowledgeNode> projectionMembers = projectionMembers(userId, noteNode);
    Set<Long> memberIds = nodeIds(projectionMembers);
    List<KnowledgeRelation> relations = relationMapper.selectList(new QueryWrapper<KnowledgeRelation>()
        .eq("user_id", userId)
        .and(w -> w.eq("source_record_id", noteId)
            .or().in("source_node_id", memberIds)
            .or().in("target_node_id", memberIds)));

    Map<Long, KnowledgeNode> nodeById = loadRelationNodes(userId, relations);
    for (KnowledgeNode member : projectionMembers) nodeById.put(member.id, member);
    return graphFrom(userId, nodeById, relations);
  }

  public KnowledgeNodeDetailDTO nodeDetail(Long userId, Long nodeId) {
    KnowledgeNode requestedNode = requireNode(userId, nodeId);
    List<KnowledgeNode> members = projectionMembers(userId, requestedNode);
    KnowledgeNode displayedNode = preferredNote(members, requestedNode);
    Set<Long> memberIds = nodeIds(members);
    List<KnowledgeRelation> relations = relationMapper.selectList(new QueryWrapper<KnowledgeRelation>()
        .eq("user_id", userId)
        .and(w -> w.in("source_node_id", memberIds).or().in("target_node_id", memberIds)));
    Map<Long, KnowledgeNode> nodeById = loadRelationNodes(userId, relations);
    for (KnowledgeNode member : members) nodeById.put(member.id, member);
    KnowledgeGraphDTO projected = graphFrom(userId, nodeById, relations);
    GraphNodeDTO root = projected.nodes.stream()
        .filter(node -> displayedNode.id.equals(node.knowledgeNodeId))
        .findFirst()
        .orElseGet(() -> basicNodeDTO(displayedNode, 0, 0));
    return new KnowledgeNodeDetailDTO(
        root,
        sourcesForNodes(userId, members, relations),
        projected.nodes.stream()
            .filter(node -> !displayedNode.id.equals(node.knowledgeNodeId))
            .toList(),
        List.of()
    );
  }

  public KnowledgeGraphDTO neighbors(Long userId, Long nodeId, Integer depth, String relationTypes) {
    KnowledgeNode root = requireNode(userId, nodeId);
    int maxDepth = normalizeDepth(depth);
    Set<String> allowedTypes = normalizeRelationTypes(relationTypes);

    Map<Long, KnowledgeNode> nodeById = new LinkedHashMap<>();
    Map<Long, KnowledgeRelation> relationById = new LinkedHashMap<>();
    Set<Long> visited = new LinkedHashSet<>();
    Set<Long> frontier = new LinkedHashSet<>();
    for (KnowledgeNode member : projectionMembers(userId, root)) {
      nodeById.put(member.id, member);
      visited.add(member.id);
      frontier.add(member.id);
    }

    for (int currentDepth = 0; currentDepth < maxDepth && !frontier.isEmpty(); currentDepth++) {
      List<KnowledgeRelation> levelRelations = relationsForNodeIds(userId, frontier, allowedTypes);
      Set<Long> nextNodeIds = new LinkedHashSet<>();
      for (KnowledgeRelation relation : levelRelations) {
        relationById.put(relation.id, relation);
        if (!visited.contains(relation.sourceNodeId)) nextNodeIds.add(relation.sourceNodeId);
        if (!visited.contains(relation.targetNodeId)) nextNodeIds.add(relation.targetNodeId);
      }
      List<KnowledgeNode> nextNodes = loadNodes(userId, nextNodeIds);
      frontier = new LinkedHashSet<>();
      for (KnowledgeNode node : nextNodes) {
        nodeById.put(node.id, node);
        if (visited.add(node.id)) frontier.add(node.id);
      }
    }

    return graphFrom(userId, nodeById, new ArrayList<>(relationById.values()));
  }

  private KnowledgeGraphDTO graphFrom(Long userId, Map<Long, KnowledgeNode> nodeById, List<KnowledgeRelation> relations) {
    Map<Long, KnowledgeNode> projectedNodes = new LinkedHashMap<>(nodeById);
    Map<Long, KnowledgeNode> titleReplacementByNodeId = titleReplacements(userId, nodeById.values());
    for (Map.Entry<Long, KnowledgeNode> replacement : titleReplacementByNodeId.entrySet()) {
      projectedNodes.remove(replacement.getKey());
      projectedNodes.put(replacement.getValue().id, replacement.getValue());
    }

    Map<String, GraphEdgeDTO> edgeByEndpoints = new LinkedHashMap<>();
    Map<Long, Set<Long>> sourceRecordIdsByNode = new LinkedHashMap<>();
    for (KnowledgeNode originalNode : nodeById.values()) {
      KnowledgeNode replacement = titleReplacementByNodeId.get(originalNode.id);
      Long projectedId = replacement == null ? originalNode.id : replacement.id;
      addRecordId(sourceRecordIdsByNode, projectedId, originalNode.sourceRecordId);
      if (replacement != null) addRecordId(sourceRecordIdsByNode, projectedId, replacement.sourceRecordId);
    }
    for (KnowledgeRelation relation : relations) {
      if (!nodeById.containsKey(relation.sourceNodeId) || !nodeById.containsKey(relation.targetNodeId)) continue;
      KnowledgeNode sourceReplacement = titleReplacementByNodeId.get(relation.sourceNodeId);
      KnowledgeNode targetReplacement = titleReplacementByNodeId.get(relation.targetNodeId);
      Long sourceId = sourceReplacement == null ? relation.sourceNodeId : sourceReplacement.id;
      Long targetId = targetReplacement == null ? relation.targetNodeId : targetReplacement.id;
      if (sourceId.equals(targetId)) continue;
      String relationType = relation.relationType;
      if (RelationType.CONTAINS.value().equals(relationType)
          && (sourceReplacement != null || targetReplacement != null)) {
        relationType = RelationType.RELATED_TO.value();
      }
      String edgeKey = sourceId + "->" + targetId + ':' + relationType;
      GraphEdgeDTO edge = toEdgeDTO(relation, sourceId, targetId, relationType);
      GraphEdgeDTO existing = edgeByEndpoints.putIfAbsent(edgeKey, edge);
      if (existing != null) existing.evidence = mergeEvidence(existing.evidence, edge.evidence);
      addRecordId(sourceRecordIdsByNode, sourceId, relation.sourceRecordId);
      addRecordId(sourceRecordIdsByNode, targetId, relation.sourceRecordId);
    }
    Map<Long, Long> relationCountByNode = new LinkedHashMap<>();
    for (GraphEdgeDTO edge : edgeByEndpoints.values()) {
      increment(relationCountByNode, parseGraphNodeId(edge.source));
      increment(relationCountByNode, parseGraphNodeId(edge.target));
    }
    Set<Long> allSourceRecordIds = new LinkedHashSet<>();
    for (Set<Long> recordIds : sourceRecordIdsByNode.values()) allSourceRecordIds.addAll(recordIds);
    Set<Long> existingRecordIds = existingRecordIds(userId, allSourceRecordIds);

    List<GraphNodeDTO> nodes = projectedNodes.values().stream()
        .map(node -> basicNodeDTO(
            node,
            relationCountByNode.getOrDefault(node.id, 0L),
            sourceRecordIdsByNode.getOrDefault(node.id, Set.of()).stream()
                .filter(existingRecordIds::contains)
                .count()))
        .toList();
    return new KnowledgeGraphDTO(nodes, new ArrayList<>(edgeByEndpoints.values()));
  }

  private Map<Long, KnowledgeNode> titleReplacements(Long userId, Iterable<KnowledgeNode> visibleNodes) {
    Map<String, KnowledgeNode> noteByNormalizedTitle = new LinkedHashMap<>();
    for (KnowledgeNode note : nodeMapper.selectList(new QueryWrapper<KnowledgeNode>()
        .eq("user_id", userId)
        .eq("node_type", StructuredKnowledgeService.NODE_NOTE)
        .orderByDesc("updated_at")
        .orderByDesc("id"))) {
      noteByNormalizedTitle.putIfAbsent(normalizedName(note), note);
    }

    Map<Long, KnowledgeNode> replacements = new LinkedHashMap<>();
    for (KnowledgeNode node : visibleNodes) {
      if (node == null || node.id == null || StructuredKnowledgeService.NODE_NOTE.equals(node.nodeType)) continue;
      KnowledgeNode matchingNote = noteByNormalizedTitle.get(normalizedName(node));
      if (matchingNote != null) replacements.put(node.id, matchingNote);
    }
    return replacements;
  }

  private String normalizedName(KnowledgeNode node) {
    if (node == null) return "";
    if (node.normalizedName != null && !node.normalizedName.isBlank()) return node.normalizedName;
    return node.name == null ? "" : node.name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
  }

  private KnowledgeNode requireNode(Long userId, Long nodeId) {
    KnowledgeNode node = nodeMapper.selectOne(new QueryWrapper<KnowledgeNode>()
        .eq("id", nodeId)
        .eq("user_id", userId));
    if (node == null) throw new IllegalArgumentException("知识节点不存在或无权访问");
    return node;
  }

  private GraphNodeDTO basicNodeDTO(KnowledgeNode node, long relationCount, long sourceCount) {
    GraphNodeDTO dto = new GraphNodeDTO();
    dto.id = graphNodeId(node.id);
    dto.knowledgeNodeId = node.id;
    dto.type = normalizeNodeType(node.nodeType);
    dto.nodeType = node.nodeType;
    dto.name = node.name;
    dto.label = node.name;
    dto.category = dto.type;
    dto.description = node.description;
    dto.importance = null;
    dto.sourceRecordId = node.sourceRecordId;
    dto.recordId = node.sourceRecordId;
    dto.relationCount = relationCount;
    dto.sourceCount = sourceCount;
    return dto;
  }

  private GraphEdgeDTO toEdgeDTO(KnowledgeRelation relation, Long sourceNodeId, Long targetNodeId,
                                 String relationType) {
    GraphEdgeDTO dto = new GraphEdgeDTO();
    dto.id = "relation-" + relation.id;
    dto.source = graphNodeId(sourceNodeId);
    dto.target = graphNodeId(targetNodeId);
    dto.relationType = relationType;
    dto.type = relationType;
    dto.label = relationType;
    dto.weight = relation.weight;
    dto.confidence = relation.confidence;
    dto.evidence = relation.evidenceText;
    return dto;
  }

  private List<KnowledgeNodeDetailDTO.SourceRecordDTO> sourcesForNodes(
      Long userId, List<KnowledgeNode> nodes, List<KnowledgeRelation> relations) {
    Set<Long> recordIds = new LinkedHashSet<>();
    for (KnowledgeNode node : nodes) if (node.sourceRecordId != null) recordIds.add(node.sourceRecordId);
    for (KnowledgeRelation relation : relations) {
      if (relation.sourceRecordId != null) recordIds.add(relation.sourceRecordId);
    }
    if (recordIds.isEmpty()) return List.of();
    return recordMapper.selectList(new QueryWrapper<GrowthRecord>()
            .eq("user_id", userId)
            .in("id", recordIds)
            .orderByDesc("created_at"))
        .stream()
        .map(record -> new KnowledgeNodeDetailDTO.SourceRecordDTO(
            record.id, record.title, snippet(record.summary, record.content)))
        .toList();
  }

  private List<KnowledgeNode> projectionMembers(Long userId, KnowledgeNode node) {
    String name = normalizedName(node);
    if (name.isBlank()) return List.of(node);
    List<KnowledgeNode> members = nodeMapper.selectList(new QueryWrapper<KnowledgeNode>()
        .eq("user_id", userId)
        .eq("normalized_name", name));
    if (members.stream().noneMatch(member -> node.id.equals(member.id))) {
      members = new ArrayList<>(members);
      members.add(node);
    }
    return members;
  }

  private KnowledgeNode preferredNote(List<KnowledgeNode> members, KnowledgeNode fallback) {
    return members.stream()
        .filter(node -> StructuredKnowledgeService.NODE_NOTE.equals(node.nodeType))
        .max(Comparator
            .comparing((KnowledgeNode node) -> node.updatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(node -> node.id, Comparator.nullsFirst(Comparator.naturalOrder())))
        .orElse(fallback);
  }

  private Set<Long> nodeIds(Iterable<KnowledgeNode> nodes) {
    Set<Long> ids = new LinkedHashSet<>();
    for (KnowledgeNode node : nodes) if (node != null && node.id != null) ids.add(node.id);
    return ids;
  }

  private List<KnowledgeRelation> relationsForNodeIds(Long userId, Set<Long> nodeIds,
                                                       Set<String> relationTypes) {
    if (nodeIds == null || nodeIds.isEmpty()) return List.of();
    return relationMapper.selectList(new QueryWrapper<KnowledgeRelation>()
        .eq("user_id", userId)
        .and(w -> w.in("source_node_id", nodeIds).or().in("target_node_id", nodeIds)))
        .stream()
        .filter(relation -> relationTypes == null || relationTypes.isEmpty() || relationTypes.contains(relation.relationType))
        .toList();
  }

  private Map<Long, KnowledgeNode> loadRelationNodes(Long userId, List<KnowledgeRelation> relations) {
    Set<Long> ids = new LinkedHashSet<>();
    for (KnowledgeRelation relation : relations) {
      if (relation.sourceNodeId != null) ids.add(relation.sourceNodeId);
      if (relation.targetNodeId != null) ids.add(relation.targetNodeId);
    }
    Map<Long, KnowledgeNode> result = new LinkedHashMap<>();
    for (KnowledgeNode node : loadNodes(userId, ids)) result.put(node.id, node);
    return result;
  }

  private List<KnowledgeNode> loadNodes(Long userId, Set<Long> nodeIds) {
    if (nodeIds == null || nodeIds.isEmpty()) return List.of();
    return nodeMapper.selectList(new QueryWrapper<KnowledgeNode>()
        .eq("user_id", userId)
        .in("id", nodeIds));
  }

  private Set<Long> existingRecordIds(Long userId, Set<Long> recordIds) {
    if (recordIds == null || recordIds.isEmpty()) return Set.of();
    Set<Long> existing = new LinkedHashSet<>();
    for (GrowthRecord record : recordMapper.selectList(new QueryWrapper<GrowthRecord>()
        .eq("user_id", userId)
        .in("id", recordIds))) {
      existing.add(record.id);
    }
    return existing;
  }

  private void addRecordId(Map<Long, Set<Long>> recordIdsByNode, Long nodeId, Long recordId) {
    if (nodeId == null || recordId == null) return;
    recordIdsByNode.computeIfAbsent(nodeId, ignored -> new LinkedHashSet<>()).add(recordId);
  }

  private void increment(Map<Long, Long> counts, Long nodeId) {
    if (nodeId != null) counts.merge(nodeId, 1L, Long::sum);
  }

  private Long parseGraphNodeId(String graphId) {
    return Long.valueOf(graphId.substring("knowledge-".length()));
  }

  private String mergeEvidence(String existing, String additional) {
    String left = existing == null ? "" : existing.trim();
    String right = additional == null ? "" : additional.trim();
    if (right.isBlank() || left.equals(right) || left.contains(right)) return left;
    if (left.isBlank()) return right;
    return left + "\n---\n" + right;
  }

  private Set<String> normalizeRelationTypes(String relationTypes) {
    if (relationTypes == null || relationTypes.isBlank()) return Set.of();
    Set<String> types = new LinkedHashSet<>();
    for (String raw : relationTypes.split(",")) {
      String type = raw == null ? "" : raw.trim();
      if (!type.isBlank()) types.add(RelationType.normalize(type));
    }
    return types;
  }

  private int normalizeDepth(Integer depth) {
    if (depth == null) return DEFAULT_DEPTH;
    return Math.max(1, Math.min(MAX_DEPTH, depth));
  }

  private String graphNodeId(Long nodeId) {
    return "knowledge-" + nodeId;
  }

  private String normalizeNodeType(String nodeType) {
    return nodeType == null ? "concept" : nodeType.toLowerCase(Locale.ROOT);
  }

  private String snippet(String summary, String content) {
    String value = summary != null && !summary.isBlank() ? summary : content;
    if (value == null) return "";
    String clean = value.replaceAll("\\s+", " ").trim();
    return clean.length() <= 140 ? clean : clean.substring(0, 140);
  }

}
