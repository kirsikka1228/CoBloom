package com.cobloom.controller;

import com.cobloom.config.UserContext;
import com.cobloom.dto.KnowledgeGraphDTO;
import com.cobloom.dto.KnowledgeNodeDetailDTO;
import com.cobloom.service.GraphService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/graph")
public class GraphController {
  private final GraphService graphService;

  public GraphController(GraphService graphService) {
    this.graphService = graphService;
  }

  @GetMapping
  public KnowledgeGraphDTO graph() {
    return graphService.graph(UserContext.userId());
  }

  @GetMapping("/note/{noteId}")
  public KnowledgeGraphDTO noteGraph(@PathVariable Long noteId) {
    return graphService.noteGraph(UserContext.userId(), noteId);
  }

  @GetMapping("/nodes/{nodeId}")
  public KnowledgeNodeDetailDTO nodeDetail(@PathVariable Long nodeId) {
    return graphService.nodeDetail(UserContext.userId(), nodeId);
  }

  @GetMapping("/nodes/{nodeId}/neighbors")
  public KnowledgeGraphDTO nodeNeighbors(@PathVariable Long nodeId,
                                         @RequestParam(value = "depth", required = false) Integer depth,
                                         @RequestParam(value = "relationTypes", required = false) String relationTypes) {
    return graphService.nodeNeighbors(UserContext.userId(), nodeId, depth, relationTypes);
  }
}
