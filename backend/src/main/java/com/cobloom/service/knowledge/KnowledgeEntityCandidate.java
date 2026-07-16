package com.cobloom.service.knowledge;

import java.util.List;

public record KnowledgeEntityCandidate(
    String name,
    String type,
    String description,
    List<Integer> evidenceChunkIds
) {
  public KnowledgeEntityCandidate(String name, String type, String description) {
    this(name, type, description, List.of());
  }
}
