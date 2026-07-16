package com.cobloom.service.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RelationTypeTest {
  @Test
  void normalizeKeepsCanonicalRelationTypes() {
    assertEquals("contains", RelationType.normalize("contains"));
    assertEquals("related_to", RelationType.normalize("related_to"));
    assertEquals("prerequisite", RelationType.normalize("prerequisite"));
    assertEquals("part_of", RelationType.normalize("part_of"));
  }

  @Test
  void normalizeMapsLegacyRelationTypes() {
    assertEquals("related_to", RelationType.normalize("related"));
    assertEquals("related_to", RelationType.normalize("similar_to"));
    assertEquals("prerequisite", RelationType.normalize("depends_on"));
  }

  @Test
  void normalizeMentionsByNodeType() {
    assertEquals("contains", RelationType.normalize("mentions", "NOTE", "CONCEPT"));
    assertEquals("contains", RelationType.normalize("mentions", "ENTITY", "NOTE"));
    assertEquals("related_to", RelationType.normalize("mentions", "ENTITY", "CONCEPT"));
  }

  @Test
  void normalizeUnknownRelationAsRelatedTo() {
    assertEquals("related_to", RelationType.normalize(null));
    assertEquals("related_to", RelationType.normalize(""));
    assertEquals("related_to", RelationType.normalize("custom_relation"));
  }
}
