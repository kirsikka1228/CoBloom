package com.cobloom.service;

import com.cobloom.dto.KnowledgeGraphDTO;
import com.cobloom.dto.KnowledgeNodeDetailDTO;
import com.cobloom.service.knowledge.KnowledgeGraphQueryService;
import org.springframework.stereotype.Service;

@Service
public class GraphService {
  private final KnowledgeGraphQueryService knowledgeGraphQueryService;

  public GraphService(KnowledgeGraphQueryService knowledgeGraphQueryService) {
    this.knowledgeGraphQueryService = knowledgeGraphQueryService;
  }

  public KnowledgeGraphDTO graph(Long userId) {
    return knowledgeGraphQueryService.graph(userId);
  }

  public KnowledgeGraphDTO noteGraph(Long userId, Long noteId) {
    return knowledgeGraphQueryService.noteGraph(userId, noteId);
  }

  public KnowledgeNodeDetailDTO nodeDetail(Long userId, Long nodeId) {
    return knowledgeGraphQueryService.nodeDetail(userId, nodeId);
  }

  public KnowledgeGraphDTO nodeNeighbors(Long userId, Long nodeId, Integer depth, String relationTypes) {
    return knowledgeGraphQueryService.neighbors(userId, nodeId, depth, relationTypes);
  }
}
