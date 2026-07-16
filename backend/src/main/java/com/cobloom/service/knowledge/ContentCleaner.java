package com.cobloom.service.knowledge;

import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ContentCleaner {
  private static final Pattern MARKDOWN_IMAGE = Pattern.compile("!\\[[^\\]]*]\\([^)]*\\)");
  private static final Pattern IMAGE_PLACEHOLDER = Pattern.compile(
      "(?i)(?<![\\p{Alnum}_-])image-[\\p{Alnum}][\\p{Alnum}_.-]{3,}(?![\\p{Alnum}_-])"
  );

  public String clean(String markdown) {
    if (markdown == null) return "";
    String normalized = markdown
        .replace("\r\n", "\n")
        .replace('\r', '\n');
    String[] lines = normalized.split("\n", -1);
    StringBuilder cleaned = new StringBuilder();
    int blankCount = 0;
    boolean inCodeFence = false;
    for (String line : lines) {
      String trimmedRight = removeImageNoise(line).replaceFirst("\\s+$", "");
      String trimmed = trimmedRight.trim();
      if (trimmed.startsWith("```")) {
        inCodeFence = !inCodeFence;
      }
      if (!inCodeFence && trimmedRight.isBlank()) {
        blankCount++;
        if (blankCount <= 2) cleaned.append('\n');
      } else if (trimmedRight.isBlank()) {
        cleaned.append('\n');
      } else {
        blankCount = 0;
        cleaned.append(trimmedRight).append('\n');
      }
    }
    return cleaned.toString().trim();
  }

  public String cleanKnowledgeText(String text) {
    if (text == null) return "";
    String normalized = text
        .replace("\r\n", "\n")
        .replace('\r', '\n');
    String[] lines = normalized.split("\n", -1);
    StringBuilder cleaned = new StringBuilder();
    int blankCount = 0;
    for (String line : lines) {
      String trimmedRight = removeImageNoise(line).replaceFirst("\\s+$", "");
      if (trimmedRight.isBlank()) {
        blankCount++;
        if (blankCount <= 2) cleaned.append('\n');
      } else {
        blankCount = 0;
        cleaned.append(trimmedRight).append('\n');
      }
    }
    return cleaned.toString().trim();
  }

  public boolean isImageOnlyLine(String line) {
    return removeImageNoise(line).trim().isBlank()
        && line != null
        && !line.trim().isBlank();
  }

  private String removeImageNoise(String text) {
    if (text == null || text.isBlank()) return "";
    return IMAGE_PLACEHOLDER.matcher(MARKDOWN_IMAGE.matcher(text).replaceAll(" ")).replaceAll(" ");
  }
}
