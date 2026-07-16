package com.cobloom.service.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ContentCleanerTest {
  private final ContentCleaner cleaner = new ContentCleaner();

  @Test
  void nullContentReturnsEmptyText() {
    assertEquals("", cleaner.clean(null));
    assertEquals("", cleaner.cleanKnowledgeText(null));
  }

  @Test
  void markdownImagesAndImagePlaceholdersAreRemoved() {
    String cleaned = cleaner.clean("""
        # Java

        ![diagram](image.png)
        image-a1b2c3.png
        Spring Boot
        """);

    assertFalse(cleaned.contains("image.png"));
    assertFalse(cleaned.contains("image-a1b2c3.png"));
    assertTrue(cleaned.contains("Spring Boot"));
  }

  @Test
  void excessiveBlankLinesAreLimitedToTwoOutsideCodeFences() {
    String cleaned = cleaner.clean("first\n\n\n\n\nsecond");

    assertFalse(cleaned.contains("\n\n\n\n"));
    assertTrue(cleaned.contains("first"));
    assertTrue(cleaned.contains("second"));
  }

  @Test
  void imageOnlyLineIsDetectedButNormalTextIsNot() {
    assertTrue(cleaner.isImageOnlyLine("![diagram](image.png)"));
    assertFalse(cleaner.isImageOnlyLine("diagram description"));
    assertFalse(cleaner.isImageOnlyLine(null));
  }
}
