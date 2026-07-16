package com.cobloom.service.knowledge;

import java.util.List;

public record KnowledgeSummary(
    String coreTopic,
    List<String> mainConclusions,
    List<String> technicalKeywords,
    List<String> relatedKnowledge
) {}
