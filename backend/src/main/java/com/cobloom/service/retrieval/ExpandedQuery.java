package com.cobloom.service.retrieval;

import java.util.List;

public record ExpandedQuery(
    String original,
    List<String> terms,
    List<String> aliases,
    List<String> domainHints,
    String expandedText
) {
  public List<String> allTerms() {
    return java.util.stream.Stream.of(terms, aliases, domainHints)
        .flatMap(List::stream)
        .filter(term -> term != null && !term.isBlank())
        .distinct()
        .toList();
  }
}
