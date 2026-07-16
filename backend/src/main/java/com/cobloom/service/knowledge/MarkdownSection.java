package com.cobloom.service.knowledge;

public record MarkdownSection(
    String heading,
    String sectionPath,
    String content
) {}
