package com.cobloom.service.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class MarkdownParser {
  private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*$");
  private final ContentCleaner contentCleaner;

  public MarkdownParser(ContentCleaner contentCleaner) {
    this.contentCleaner = contentCleaner;
  }

  public List<MarkdownSection> parse(String markdown) {
    String text = contentCleaner.cleanKnowledgeText(markdown);
    List<MarkdownSection> sections = new ArrayList<>();
    List<String> headingStack = new ArrayList<>();
    String currentHeading = "";
    String currentPath = "";
    StringBuilder currentContent = new StringBuilder();
    boolean inCodeFence = false;

    for (String line : text.split("\n", -1)) {
      String trimmed = line.trim();
      if (trimmed.startsWith("```")) {
        inCodeFence = !inCodeFence;
        currentContent.append(line).append('\n');
        continue;
      }
      if (!inCodeFence && contentCleaner.isImageOnlyLine(line)) {
        continue;
      }

      Matcher matcher = HEADING.matcher(line);
      if (!inCodeFence && matcher.matches()) {
        addSection(sections, currentHeading, currentPath, currentContent);
        currentContent.setLength(0);

        int level = matcher.group(1).length();
        String heading = matcher.group(2).trim();
        while (headingStack.size() >= level) {
          headingStack.remove(headingStack.size() - 1);
        }
        headingStack.add(heading);
        currentHeading = heading;
        currentPath = String.join(" / ", headingStack);
      } else {
        currentContent.append(line).append('\n');
      }
    }

    addSection(sections, currentHeading, currentPath, currentContent);
    if (sections.isEmpty() && !text.isBlank()) {
      sections.add(new MarkdownSection("", "", text.trim()));
    }
    return sections;
  }

  private void addSection(List<MarkdownSection> sections, String heading, String sectionPath, StringBuilder content) {
    String body = content.toString().trim();
    if (body.isBlank() && (sectionPath == null || sectionPath.isBlank())) return;
    sections.add(new MarkdownSection(
        heading == null ? "" : heading,
        sectionPath == null ? "" : sectionPath,
        body
    ));
  }
}
