package com.cobloom.service.knowledge;

import com.cobloom.entity.GrowthRecord;
import com.cobloom.service.ai.AIService;
import com.cobloom.service.rag.EmbeddingService;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeCompilerService {
  private static final Logger log = LoggerFactory.getLogger(KnowledgeCompilerService.class);
  private static final int MAX_CONCEPTS = 12;
  private static final int MAX_ENTITIES = 15;
  private static final int MAX_RELATIONS = 20;
  private static final int MAX_RELATION_CONTEXT_CHARS = 6_000;
  private static final int MAX_RELATION_CONTEXT_CHUNKS = 12;
  private static final Pattern EVIDENCE_PATTERN = Pattern.compile("^\\[C(\\d+)]\\s*(.*)$", Pattern.DOTALL);

  private final ContentCleaner contentCleaner;
  private final MarkdownParser markdownParser;
  private final ChunkGenerator chunkGenerator;
  private final EmbeddingService embeddingService;
  private final AIService aiService;

  public KnowledgeCompilerService(ContentCleaner contentCleaner, MarkdownParser markdownParser,
                                  ChunkGenerator chunkGenerator, EmbeddingService embeddingService,
                                  AIService aiService) {
    this.contentCleaner = contentCleaner;
    this.markdownParser = markdownParser;
    this.chunkGenerator = chunkGenerator;
    this.embeddingService = embeddingService;
    this.aiService = aiService;
  }

  public KnowledgeCompileResult compile(GrowthRecord record) {
    return compile(record, stage -> {});
  }

  public KnowledgeCompileResult compile(GrowthRecord record, Consumer<GraphStage> progress) {
    log.info("KnowledgeCompiler.compile start recordId={}, userId={}, title={}",
        record == null ? null : record.id,
        record == null ? null : record.userId,
        record == null ? null : record.title);
    String rawContent = record == null ? "" : record.content;
    String cleanedContent = contentCleaner.clean(rawContent);
    List<MarkdownSection> sections = markdownParser.parse(cleanedContent);
    List<KnowledgeChunkCandidate> rawChunks = chunkGenerator.generate(sections);

    progress.accept(GraphStage.EXTRACTING_STRUCTURE);
    log.info("KnowledgeCompiler stage started recordId={}, stage=extract_structure, chunkCount={}",
        record == null ? null : record.id, rawChunks.size());
    KnowledgeStructureResult structure = aiService.extractStructure(formatChunks(rawChunks));
    List<KnowledgeConceptCandidate> concepts = normalizeConcepts(structure, rawChunks);
    List<KnowledgeEntityCandidate> entities = normalizeEntities(structure, rawChunks, concepts);
    String summary = truncate(structure == null ? null : structure.summary(), 500);
    if (summary.isBlank()) summary = "知识记录";
    log.info("KnowledgeCompiler stage finished recordId={}, stage=extract_structure, conceptCount={}, entityCount={}",
        record == null ? null : record.id, concepts.size(), entities.size());

    List<KnowledgeNodeReference> nodeReferences = buildNodeReferences(concepts, entities);
    List<KnowledgeChunkCandidate> relationChunks = selectRelationChunks(rawChunks, concepts, entities);
    progress.accept(GraphStage.EXTRACTING_RELATIONS);
    log.info("KnowledgeCompiler stage started recordId={}, stage=extract_relations, nodeCount={}, evidenceChunkCount={}, evidenceChars={}",
        record == null ? null : record.id, nodeReferences.size(), relationChunks.size(), contentLength(relationChunks));
    List<KnowledgeRelationCandidate> relations = validateRelations(
        aiService.extractRelations(nodeReferences, relationChunks), nodeReferences, relationChunks);
    log.info("KnowledgeCompiler stage finished recordId={}, stage=extract_relations, relationCount={}",
        record == null ? null : record.id, relations.size());

    List<KnowledgeChunkCandidate> chunks = embedChunks(rawChunks);
    List<String> keywords = deriveKeywords(concepts, entities);

    log.info("KnowledgeCompiler.compile finished recordId={}, userId={}, cleanedLength={}, sectionCount={}, chunkCount={}, keywordCount={}, conceptCount={}, entityCount={}, relationCount={}",
        record == null ? null : record.id,
        record == null ? null : record.userId,
        cleanedContent.length(),
        sections.size(),
        chunks.size(),
        keywords == null ? 0 : keywords.size(),
        concepts == null ? 0 : concepts.size(),
        entities == null ? 0 : entities.size(),
        relations == null ? 0 : relations.size());
    return new KnowledgeCompileResult(
        cleanedContent,
        summary,
        keywords,
        concepts,
        entities,
        relations,
        chunks
    );
  }

  private List<KnowledgeChunkCandidate> embedChunks(List<KnowledgeChunkCandidate> chunks) {
    List<KnowledgeChunkCandidate> embedded = new ArrayList<>();
    for (KnowledgeChunkCandidate chunk : chunks) {
      String content = contentCleaner.cleanKnowledgeText(chunk.content());
      embedded.add(new KnowledgeChunkCandidate(
          chunk.chunkIndex(),
          chunk.heading(),
          chunk.sectionPath(),
          content,
          embeddingService.embedToString(content)
      ));
    }
    return embedded;
  }

  private String formatChunks(List<KnowledgeChunkCandidate> chunks) {
    StringBuilder content = new StringBuilder();
    for (KnowledgeChunkCandidate chunk : chunks) {
      content.append("[C").append(chunk.chunkIndex()).append("] ")
          .append(chunk.content() == null ? "" : chunk.content()).append("\n\n");
    }
    return content.toString().trim();
  }

  private List<KnowledgeConceptCandidate> normalizeConcepts(KnowledgeStructureResult structure,
                                                             List<KnowledgeChunkCandidate> chunks) {
    Map<String, KnowledgeConceptCandidate> result = new LinkedHashMap<>();
    List<KnowledgeConceptCandidate> candidates = structure == null || structure.concepts() == null
        ? List.of() : structure.concepts();
    for (KnowledgeConceptCandidate candidate : candidates) {
      if (candidate == null) continue;
      String name = truncate(candidate.name(), 80);
      String key = canonicalName(name);
      if (key.isBlank() || result.containsKey(key)) continue;
      result.put(key, new KnowledgeConceptCandidate(
          name, truncate(candidate.description(), 120),
          validEvidenceChunkIds(candidate.evidenceChunkIds(), chunks, name)));
      if (result.size() >= MAX_CONCEPTS) break;
    }
    return new ArrayList<>(result.values());
  }

  private List<KnowledgeEntityCandidate> normalizeEntities(KnowledgeStructureResult structure,
                                                            List<KnowledgeChunkCandidate> chunks,
                                                            List<KnowledgeConceptCandidate> concepts) {
    Set<String> usedNames = new LinkedHashSet<>();
    for (KnowledgeConceptCandidate concept : concepts) usedNames.add(canonicalName(concept.name()));
    List<KnowledgeEntityCandidate> result = new ArrayList<>();
    List<KnowledgeEntityCandidate> candidates = structure == null || structure.entities() == null
        ? List.of() : structure.entities();
    for (KnowledgeEntityCandidate candidate : candidates) {
      if (candidate == null) continue;
      String name = truncate(candidate.name(), 80);
      String key = canonicalName(name);
      if (key.isBlank() || !usedNames.add(key)) continue;
      result.add(new KnowledgeEntityCandidate(
          name, truncate(candidate.type(), 40), truncate(candidate.description(), 120),
          validEvidenceChunkIds(candidate.evidenceChunkIds(), chunks, name)));
      if (result.size() >= MAX_ENTITIES) break;
    }
    return result;
  }

  private List<Integer> validEvidenceChunkIds(List<Integer> requestedIds,
                                              List<KnowledgeChunkCandidate> chunks,
                                              String nodeName) {
    Set<Integer> available = new LinkedHashSet<>();
    for (KnowledgeChunkCandidate chunk : chunks) available.add(chunk.chunkIndex());
    Set<Integer> valid = new LinkedHashSet<>();
    if (requestedIds != null) {
      for (Integer id : requestedIds) {
        if (id != null && available.contains(id)) valid.add(id);
        if (valid.size() >= 3) break;
      }
    }
    if (valid.isEmpty() && nodeName != null && !nodeName.isBlank()) {
      String needle = normalizeEvidence(nodeName);
      for (KnowledgeChunkCandidate chunk : chunks) {
        if (normalizeEvidence(chunk.content()).contains(needle)) valid.add(chunk.chunkIndex());
        if (valid.size() >= 3) break;
      }
    }
    return new ArrayList<>(valid);
  }

  private List<KnowledgeNodeReference> buildNodeReferences(List<KnowledgeConceptCandidate> concepts,
                                                            List<KnowledgeEntityCandidate> entities) {
    List<KnowledgeNodeReference> references = new ArrayList<>();
    for (KnowledgeConceptCandidate concept : concepts) {
      references.add(new KnowledgeNodeReference("N" + (references.size() + 1), concept.name(), "CONCEPT"));
    }
    for (KnowledgeEntityCandidate entity : entities) {
      references.add(new KnowledgeNodeReference("N" + (references.size() + 1), entity.name(), "ENTITY"));
    }
    return references;
  }

  private List<KnowledgeChunkCandidate> selectRelationChunks(List<KnowledgeChunkCandidate> chunks,
                                                              List<KnowledgeConceptCandidate> concepts,
                                                              List<KnowledgeEntityCandidate> entities) {
    Map<Integer, KnowledgeChunkCandidate> byId = new LinkedHashMap<>();
    for (KnowledgeChunkCandidate chunk : chunks) byId.put(chunk.chunkIndex(), chunk);
    Set<Integer> selectedIds = new LinkedHashSet<>();
    for (KnowledgeConceptCandidate concept : concepts) addWithNeighbours(selectedIds, concept.evidenceChunkIds(), byId);
    for (KnowledgeEntityCandidate entity : entities) addWithNeighbours(selectedIds, entity.evidenceChunkIds(), byId);
    if (selectedIds.isEmpty()) {
      for (KnowledgeChunkCandidate chunk : chunks) {
        selectedIds.add(chunk.chunkIndex());
        if (selectedIds.size() >= 6) break;
      }
    }

    List<KnowledgeChunkCandidate> selected = new ArrayList<>();
    int chars = 0;
    for (Integer id : selectedIds) {
      KnowledgeChunkCandidate chunk = byId.get(id);
      if (chunk == null) continue;
      int length = chunk.content() == null ? 0 : chunk.content().length();
      if (!selected.isEmpty() && chars + length > MAX_RELATION_CONTEXT_CHARS) break;
      selected.add(chunk);
      chars += length;
      if (selected.size() >= MAX_RELATION_CONTEXT_CHUNKS) break;
    }
    return selected;
  }

  private void addWithNeighbours(Set<Integer> selected, List<Integer> evidenceIds,
                                 Map<Integer, KnowledgeChunkCandidate> chunks) {
    if (evidenceIds == null) return;
    for (Integer id : evidenceIds) {
      if (id == null) continue;
      if (chunks.containsKey(id)) selected.add(id);
      if (chunks.containsKey(id - 1)) selected.add(id - 1);
      if (chunks.containsKey(id + 1)) selected.add(id + 1);
    }
  }

  private List<KnowledgeRelationCandidate> validateRelations(List<KnowledgeRelationCandidate> candidates,
                                                              List<KnowledgeNodeReference> nodes,
                                                              List<KnowledgeChunkCandidate> chunks) {
    Set<String> nodeNames = new LinkedHashSet<>();
    for (KnowledgeNodeReference node : nodes) nodeNames.add(canonicalName(node.name()));
    Map<Integer, KnowledgeChunkCandidate> chunksById = new LinkedHashMap<>();
    for (KnowledgeChunkCandidate chunk : chunks) chunksById.put(chunk.chunkIndex(), chunk);
    Set<String> seen = new LinkedHashSet<>();
    List<KnowledgeRelationCandidate> valid = new ArrayList<>();
    if (candidates == null) return valid;
    for (KnowledgeRelationCandidate candidate : candidates) {
      if (candidate == null) continue;
      String source = canonicalName(candidate.source());
      String target = canonicalName(candidate.target());
      String type = RelationType.normalizeStrict(candidate.relationType());
      if (source.isBlank() || target.isBlank() || source.equals(target)
          || !nodeNames.contains(source) || !nodeNames.contains(target) || type == null) continue;
      Matcher evidence = EVIDENCE_PATTERN.matcher(candidate.evidence() == null ? "" : candidate.evidence());
      if (!evidence.matches()) continue;
      int chunkId = Integer.parseInt(evidence.group(1));
      String quote = evidence.group(2).trim();
      KnowledgeChunkCandidate chunk = chunksById.get(chunkId);
      if (chunk == null || quote.isBlank()
          || !normalizeEvidence(chunk.content()).contains(normalizeEvidence(quote))) continue;
      String key = source + '|' + type + '|' + target;
      if (!seen.add(key)) continue;
      valid.add(new KnowledgeRelationCandidate(
          candidate.source().trim(), candidate.target().trim(), type, "[C" + chunkId + "] " + quote));
      if (valid.size() >= MAX_RELATIONS) break;
    }
    return valid;
  }

  private String canonicalName(String value) {
    if (value == null) return "";
    return Normalizer.normalize(value, Normalizer.Form.NFKC)
        .trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
  }

  private String normalizeEvidence(String value) {
    if (value == null) return "";
    return Normalizer.normalize(value, Normalizer.Form.NFKC)
        .toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{S}\\s]+", "");
  }

  private String truncate(String value, int maxLength) {
    String clean = value == null ? "" : value.trim().replaceAll("\\s+", " ");
    return clean.length() <= maxLength ? clean : clean.substring(0, maxLength);
  }

  private int contentLength(List<KnowledgeChunkCandidate> chunks) {
    int length = 0;
    for (KnowledgeChunkCandidate chunk : chunks) length += chunk.content() == null ? 0 : chunk.content().length();
    return length;
  }

  private List<String> deriveKeywords(List<KnowledgeConceptCandidate> concepts,
                                      List<KnowledgeEntityCandidate> entities) {
    Set<String> keywords = new LinkedHashSet<>();
    if (concepts != null) {
      for (KnowledgeConceptCandidate concept : concepts) {
        if (concept != null && concept.name() != null && !concept.name().isBlank()) {
          keywords.add(concept.name().trim());
          if (keywords.size() >= 8) return new ArrayList<>(keywords);
        }
      }
    }
    if (entities != null) {
      for (KnowledgeEntityCandidate entity : entities) {
        if (entity != null && entity.name() != null && !entity.name().isBlank()) {
          keywords.add(entity.name().trim());
          if (keywords.size() >= 8) return new ArrayList<>(keywords);
        }
      }
    }
    if (keywords.isEmpty()) keywords.add("知识整理");
    return new ArrayList<>(keywords);
  }
}
