package com.cobloom.service.retrieval;

import java.util.List;

public record RetrievalResult(
    String question,
    List<RetrievalCandidate> candidates
) {
  public boolean isEmpty() {
    return candidates == null || candidates.isEmpty();
  }
}
