package com.cobloom.service.knowledge;

import java.util.Locale;
import java.util.Set;

public enum RelationType {
  CONTAINS("contains"),
  RELATED_TO("related_to"),
  PREREQUISITE("prerequisite"),
  PART_OF("part_of");

  private static final Set<String> NOTE_TYPES = Set.of("NOTE", "NOTE_NODE");

  private final String value;

  RelationType(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public static String normalize(String relationType) {
    return normalize(relationType, null, null);
  }

  public static String normalizeStrict(String relationType) {
    String value = relationType == null ? "" : relationType.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    for (RelationType type : values()) {
      if (type.value.equals(value)) return type.value;
    }
    return null;
  }

  public static String normalize(String relationType, String sourceNodeType, String targetNodeType) {
    String value = relationType == null ? "" : relationType.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    return switch (value) {
      case "contains" -> CONTAINS.value;
      case "related", "related_to", "similar_to", "similar", "associated_with" -> RELATED_TO.value;
      case "depends_on", "dependency", "requires", "prerequisite" -> PREREQUISITE.value;
      case "part_of", "component_of", "belongs_to" -> PART_OF.value;
      case "mentions" -> mentionsRelation(sourceNodeType, targetNodeType);
      default -> RELATED_TO.value;
    };
  }

  private static String mentionsRelation(String sourceNodeType, String targetNodeType) {
    return isNote(sourceNodeType) || isNote(targetNodeType) ? CONTAINS.value : RELATED_TO.value;
  }

  private static boolean isNote(String nodeType) {
    return nodeType != null && NOTE_TYPES.contains(nodeType.trim().toUpperCase(Locale.ROOT));
  }
}
