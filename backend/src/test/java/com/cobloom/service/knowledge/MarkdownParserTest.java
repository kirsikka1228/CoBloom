package com.cobloom.service.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class MarkdownParserTest {
  private final MarkdownParser parser = new MarkdownParser(new ContentCleaner());

  @Test
  void plainTextWithoutHeadingBecomesOneSection() {
    List<MarkdownSection> sections = parser.parse("plain knowledge text");

    assertEquals(1, sections.size());
    assertEquals("", sections.getFirst().sectionPath());
    assertEquals("plain knowledge text", sections.getFirst().content());
  }

  @Test
  void nestedHeadingsKeepTheirFullSectionPath() {
    List<MarkdownSection> sections = parser.parse("""
        # Spring
        framework content
        ## IoC
        container content
        """);

    assertEquals(2, sections.size());
    assertEquals("Spring", sections.get(0).sectionPath());
    assertEquals("Spring / IoC", sections.get(1).sectionPath());
  }

  @Test
  void headingSyntaxInsideCodeFenceIsNotParsedAsASection() {
    List<MarkdownSection> sections = parser.parse("""
        ```markdown
        # not a real heading
        ```
        """);

    assertEquals(1, sections.size());
    assertEquals("", sections.getFirst().sectionPath());
    assertTrue(sections.getFirst().content().contains("# not a real heading"));
  }

  @Test
  void emptyInputProducesNoSections() {
    assertTrue(parser.parse("").isEmpty());
  }
}
