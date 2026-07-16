package com.cobloom.service.knowledge;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ChunkGenerator {
  private static final int TARGET_CHARS = 900;
  private static final int HARD_LIMIT_CHARS = 1200;
  private final ContentCleaner contentCleaner;

  public ChunkGenerator(ContentCleaner contentCleaner) {
    this.contentCleaner = contentCleaner;
  }

  public List<KnowledgeChunkCandidate> generate(List<MarkdownSection> sections) {
    List<KnowledgeChunkCandidate> chunks = new ArrayList<>();
    if (sections == null || sections.isEmpty()) {
      chunks.add(new KnowledgeChunkCandidate(0, "", "", "", ""));
      return chunks;
    }

    for (MarkdownSection section : sections) {
      appendSectionChunks(chunks, section);
    }

    if (chunks.isEmpty()) {
      chunks.add(new KnowledgeChunkCandidate(0, "", "", "", ""));
    }
    return reindex(chunks);
  }

  private void appendSectionChunks(List<KnowledgeChunkCandidate> chunks, MarkdownSection section) {
    String heading = section.heading() == null ? "" : section.heading();
    String sectionPath = section.sectionPath() == null ? "" : section.sectionPath();
    String body = contentCleaner.cleanKnowledgeText(section.content());
    String prefix = sectionPath.isBlank() ? "" : sectionPath + "\n\n";
    String[] paragraphs = body.split("\\n\\s*\\n");
    StringBuilder current = new StringBuilder();

    for (String paragraph : paragraphs) {
      String clean = paragraph.trim();
      if (clean.isBlank()) continue;
      if (clean.length() > HARD_LIMIT_CHARS) {
        flush(chunks, heading, sectionPath, prefix, current);
        splitLongParagraph(chunks, heading, sectionPath, prefix, clean);
        continue;
      }

      int projectedLength = prefix.length() + current.length() + clean.length() + 2;
      if (projectedLength > TARGET_CHARS && current.length() > 0) {
        flush(chunks, heading, sectionPath, prefix, current);
      }
      if (current.length() > 0) current.append("\n\n");
      current.append(clean);
    }

    flush(chunks, heading, sectionPath, prefix, current);
    if (body.isBlank() && !sectionPath.isBlank()) {
      chunks.add(new KnowledgeChunkCandidate(0, heading, sectionPath, sectionPath, ""));
    }
  }

  private void splitLongParagraph(List<KnowledgeChunkCandidate> chunks, String heading, String sectionPath,
                                  String prefix, String paragraph) {
    for (int start = 0; start < paragraph.length(); start += TARGET_CHARS) {
      String part = paragraph.substring(start, Math.min(paragraph.length(), start + TARGET_CHARS)).trim();
      if (!part.isBlank()) {
        chunks.add(new KnowledgeChunkCandidate(0, heading, sectionPath, contentCleaner.cleanKnowledgeText(prefix + part), ""));
      }
    }
  }

  private void flush(List<KnowledgeChunkCandidate> chunks, String heading, String sectionPath, String prefix,
                     StringBuilder current) {
    if (current.length() == 0) return;
    chunks.add(new KnowledgeChunkCandidate(0, heading, sectionPath, contentCleaner.cleanKnowledgeText(prefix + current), ""));
    current.setLength(0);
  }

  private List<KnowledgeChunkCandidate> reindex(List<KnowledgeChunkCandidate> chunks) {
    List<KnowledgeChunkCandidate> reindexed = new ArrayList<>();
    for (int i = 0; i < chunks.size(); i++) {
      KnowledgeChunkCandidate chunk = chunks.get(i);
      reindexed.add(new KnowledgeChunkCandidate(
          i,
          chunk.heading(),
          chunk.sectionPath(),
          chunk.content(),
          chunk.embedding()
      ));
    }
    return reindexed;
  }
}
