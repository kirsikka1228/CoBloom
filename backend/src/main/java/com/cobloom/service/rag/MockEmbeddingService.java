package com.cobloom.service.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class MockEmbeddingService implements EmbeddingService {
  private static final int DIMENSIONS = 128;

  @Override
  public double[] embed(String text) {
    double[] vector = new double[DIMENSIONS];
    for (String token : tokens(text)) {
      int hash = token.hashCode();
      int index = (hash == Integer.MIN_VALUE ? 0 : Math.abs(hash)) % DIMENSIONS;
      vector[index] += 1.0;
    }
    normalize(vector);
    return vector;
  }

  @Override
  public String embedToString(String text) {
    return toJson(embed(text));
  }

  @Override
  public double cosine(double[] embeddingA, double[] embeddingB) {
    if (embeddingA == null || embeddingB == null) return 0;
    int length = Math.min(Math.min(embeddingA.length, embeddingB.length), DIMENSIONS);
    double dot = 0, normA = 0, normB = 0;
    for (int i = 0; i < length; i++) {
      dot += embeddingA[i] * embeddingB[i];
      normA += embeddingA[i] * embeddingA[i];
      normB += embeddingB[i] * embeddingB[i];
    }
    if (normA == 0 || normB == 0) return 0;
    double value = dot / (Math.sqrt(normA) * Math.sqrt(normB));
    return Double.isFinite(value) ? value : 0;
  }

  @Override
  public double cosine(double[] embeddingA, String embeddingB) {
    return cosine(embeddingA, parse(embeddingB));
  }

  @Override
  public double cosine(String embeddingA, String embeddingB) {
    return cosine(parse(embeddingA), parse(embeddingB));
  }

  private List<String> tokens(String text) {
    String clean = text == null ? "" : text.toLowerCase(Locale.ROOT)
        .replaceAll("[\\s\\p{Punct}，。！？；：、“”‘’（）【】《》]+", " ");
    List<String> result = new ArrayList<>();
    for (String token : clean.split("\\s+")) {
      if (!token.isBlank()) result.add(token);
    }
    return result;
  }

  private void normalize(double[] vector) {
    double norm = 0;
    for (double value : vector) norm += value * value;
    if (norm == 0) return;
    double scale = Math.sqrt(norm);
    for (int i = 0; i < vector.length; i++) vector[i] = vector[i] / scale;
  }

  private String toJson(double[] vector) {
    StringBuilder json = new StringBuilder("[");
    for (int i = 0; i < vector.length; i++) {
      if (i > 0) json.append(',');
      json.append(Math.round(vector[i] * 1_000_000.0) / 1_000_000.0);
    }
    return json.append(']').toString();
  }

  private double[] parse(String embedding) {
    double[] vector = new double[DIMENSIONS];
    if (embedding == null || embedding.isBlank()) return vector;
    String raw = embedding.trim();
    if (raw.startsWith("[") && raw.endsWith("]")) raw = raw.substring(1, raw.length() - 1);
    String[] parts = raw.split(",");
    for (int i = 0; i < Math.min(parts.length, DIMENSIONS); i++) {
      try {
        vector[i] = Double.parseDouble(parts[i].trim());
      } catch (NumberFormatException ignored) {
        vector[i] = 0;
      }
    }
    return vector;
  }
}
