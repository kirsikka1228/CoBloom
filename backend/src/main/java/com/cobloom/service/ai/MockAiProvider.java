package com.cobloom.service.ai;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "cobloom.ai-provider", havingValue = "mock", matchIfMissing = true)
public class MockAiProvider implements AiProvider {
  private static final Logger log = LoggerFactory.getLogger(MockAiProvider.class);

  @Override
  public String name() {
    return "mock";
  }

  @Override
  public String complete(String systemPrompt, String userPrompt) {
    String taskName = extractTask(userPrompt);
    long startedAt = System.nanoTime();
    log.info("AI request started provider={}, task={}", name(), taskName);
    try {
      String content = extractContent(userPrompt);
      String result = switch (taskName) {
        case "knowledge-summary" -> summaryJson(content);
        case "knowledge-structure" -> structureJson(content);
        case "knowledge-keywords" -> keywordsJson(content);
        case "knowledge-relations-by-id" -> relationsByIdJson(userPrompt);
        case "qa-answer" -> answerText(content);
        default -> "{}";
      };
      log.info("AI request finished provider={}, task={}, elapsedMs={}, responseLength={}",
          name(), taskName, elapsedMillis(startedAt), result.length());
      return result;
    } catch (RuntimeException e) {
      log.warn("AI request failed provider={}, task={}, elapsedMs={}, errorType={}",
          name(), taskName, elapsedMillis(startedAt), e.getClass().getSimpleName());
      throw e;
    }
  }

  private long elapsedMillis(long startedAt) {
    return (System.nanoTime() - startedAt) / 1_000_000;
  }

  private String summaryJson(String content) {
    List<String> headings = headings(content);
    List<String> terms = knowledgeTerms(content, 8);
    String topic = !headings.isEmpty() ? headings.get(0) : (!terms.isEmpty() ? terms.get(0) : "知识记录");
    List<String> conclusions = conclusions(content);
    List<String> related = new ArrayList<>();
    for (String term : terms) {
      if (!term.equals(topic) && related.size() < 5) related.add(term);
    }
    return """
        {
          "coreTopic": "%s",
          "mainConclusions": %s,
          "technicalKeywords": %s,
          "relatedKnowledge": %s
        }
        """.formatted(escape(topic), jsonArray(conclusions), jsonArray(terms), jsonArray(related));
  }

  private String keywordsJson(String content) {
    return "{\"keywords\":" + jsonArray(knowledgeTerms(content, 8)) + "}";
  }

  private String structureJson(String content) {
    List<String> concepts = knowledgeTerms(content, 8);
    if (concepts.isEmpty()) concepts = List.of("知识整理");
    List<String> entityNames = entities(content);
    int chunkId = firstChunkId(content);
    StringBuilder json = new StringBuilder("{\"summary\":\"")
        .append(escape(shorten(safe(content).replaceAll("\\s+", " "), 300)))
        .append("\",\"concepts\":[");
    for (int i = 0; i < concepts.size(); i++) {
      if (i > 0) json.append(',');
      String name = concepts.get(i);
      json.append("{\"name\":\"").append(escape(name))
          .append("\",\"description\":\"").append(escape(name))
          .append("\",\"evidenceChunkIds\":[").append(chunkId).append("]}");
    }
    json.append("],\"entities\":[");
    for (int i = 0; i < entityNames.size(); i++) {
      if (i > 0) json.append(',');
      String name = entityNames.get(i);
      json.append("{\"name\":\"").append(escape(name))
          .append("\",\"type\":\"").append(escape(entityType(name)))
          .append("\",\"description\":\"").append(escape(name))
          .append("\",\"evidenceChunkIds\":[").append(chunkId).append("]}");
    }
    return json.append("]}").toString();
  }

  private String relationsByIdJson(String prompt) {
    Matcher nodeMatcher = Pattern.compile("(?m)^(N\\d+) \\| [^|]+ \\| (.+)$").matcher(safe(prompt));
    List<String> ids = new ArrayList<>();
    while (nodeMatcher.find() && ids.size() < 2) ids.add(nodeMatcher.group(1));
    if (ids.size() < 2) return "{\"relations\":[]}";
    Matcher chunkMatcher = Pattern.compile("(?s)\\[C(\\d+)]\\s+(.+?)(?:\\n\\n\\[C\\d+]|$)").matcher(safe(prompt));
    if (!chunkMatcher.find()) return "{\"relations\":[]}";
    int chunkId = Integer.parseInt(chunkMatcher.group(1));
    String quote = shorten(chunkMatcher.group(2).replaceAll("\\s+", " ").trim(), 120);
    return "{\"relations\":[{\"sourceId\":\"" + ids.get(0)
        + "\",\"targetId\":\"" + ids.get(1)
        + "\",\"relationType\":\"related_to\",\"evidenceChunkId\":" + chunkId
        + ",\"evidenceQuote\":\"" + escape(quote) + "\"}]}";
  }

  private int firstChunkId(String content) {
    Matcher matcher = Pattern.compile("\\[C(\\d+)]").matcher(safe(content));
    return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
  }

  private List<String> headings(String content) {
    List<String> result = new ArrayList<>();
    for (String line : safe(content).split("\\n")) {
      String clean = line.trim();
      if (clean.matches("^#{1,6}\\s+.+")) {
        result.add(clean.replaceFirst("^#{1,6}\\s+", "").trim());
      }
    }
    return result;
  }

  private List<String> knowledgeTerms(String content, int limit) {
    Set<String> terms = new LinkedHashSet<>();
    for (String heading : headings(content)) {
      if (heading.length() >= 2) terms.add(shorten(heading, 24));
      if (terms.size() >= limit) return new ArrayList<>(terms);
    }
    for (String token : safe(content).split("[\\s,，。；;、：:（）()【】\\[\\]<>《》]+")) {
      String clean = token.replaceAll("[^\\p{IsHan}A-Za-z0-9_.-]", "");
      if (isKnowledgeTerm(clean)) terms.add(shorten(clean, 24));
      if (terms.size() >= limit) break;
    }
    return new ArrayList<>(terms);
  }

  private List<String> entities(String content) {
    Set<String> result = new LinkedHashSet<>();
    for (String token : safe(content).split("[\\s,，。；;、：:（）()【】\\[\\]<>《》]+")) {
      String clean = token.replaceAll("[^A-Za-z0-9_.-]", "");
      if (isEntity(clean)) result.add(shorten(clean, 32));
      if (result.size() >= 8) break;
    }
    return new ArrayList<>(result);
  }

  private List<String> conclusions(String content) {
    List<String> result = new ArrayList<>();
    for (String line : safe(content).split("\\n")) {
      String clean = line.replaceFirst("^[-*+]\\s+", "").trim();
      if (clean.length() >= 12 && !clean.startsWith("#")) {
        result.add(shorten(clean, 80));
      }
      if (result.size() >= 4) break;
    }
    if (result.isEmpty()) result.add("该文档沉淀了可被检索、组织和关联的知识内容。");
    return result;
  }

  private boolean isKnowledgeTerm(String token) {
    if (token == null || token.length() < 3) return false;
    String lower = token.toLowerCase(Locale.ROOT);
    return token.contains("知识")
        || token.contains("检索")
        || token.contains("图谱")
        || token.contains("编译")
        || token.contains("模型")
        || token.contains("系统")
        || token.contains("实体")
        || token.contains("关系")
        || lower.contains("rag")
        || lower.contains("compiler")
        || lower.contains("retrieval")
        || lower.contains("embedding")
        || lower.contains("service");
  }

  private boolean isEntity(String token) {
    if (token == null || token.length() < 2) return false;
    return token.matches(".*[A-Z][A-Za-z0-9_.-]*.*")
        || token.matches(".*\\d+\\.\\d+.*")
        || token.endsWith("Service")
        || token.endsWith("Controller")
        || token.endsWith("Mapper");
  }

  private String entityType(String name) {
    String lower = name.toLowerCase(Locale.ROOT);
    if (lower.contains("service") || lower.contains("controller") || lower.contains("mapper")) return "CodeComponent";
    if (lower.contains("spring") || lower.contains("vue") || lower.contains("h2") || lower.contains("echarts")) return "Technology";
    if (name.matches(".*\\d+\\.\\d+.*")) return "Version";
    return "Entity";
  }

  private String extractContent(String prompt) {
    String marker = "文档内容：";
    int index = prompt.indexOf(marker);
    if (index >= 0) return prompt.substring(index + marker.length());
    marker = "知识片段：";
    index = prompt.indexOf(marker);
    return index < 0 ? prompt : prompt.substring(index + marker.length());
  }

  private String extractTask(String prompt) {
    String marker = "TASK:";
    int index = safe(prompt).indexOf(marker);
    if (index < 0) return "";
    int start = index + marker.length();
    int end = safe(prompt).indexOf('\n', start);
    return safe(prompt).substring(start, end < 0 ? safe(prompt).length() : end).trim();
  }

  private String answerText(String content) {
    String evidence = safe(content).replaceAll("\\s+", " ").trim();
    if (evidence.isBlank()) return "知识库中没有足够信息";
    return "根据知识库内容，相关信息是：" + shorten(evidence, 220);
  }

  private String jsonArray(List<String> values) {
    StringBuilder json = new StringBuilder("[");
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) json.append(',');
      json.append('"').append(escape(values.get(i))).append('"');
    }
    return json.append(']').toString();
  }

  private String shorten(String value, int maxLength) {
    String clean = safe(value).trim();
    return clean.length() <= maxLength ? clean : clean.substring(0, maxLength);
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }

  private String escape(String value) {
    return safe(value).replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
