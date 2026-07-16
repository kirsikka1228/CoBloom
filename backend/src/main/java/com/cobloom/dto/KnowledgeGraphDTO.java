package com.cobloom.dto;

import java.util.List;

public class KnowledgeGraphDTO {
  public List<GraphNodeDTO> nodes;
  public List<GraphEdgeDTO> edges;

  public KnowledgeGraphDTO(List<GraphNodeDTO> nodes, List<GraphEdgeDTO> edges) {
    this.nodes = nodes;
    this.edges = edges;
  }
}
