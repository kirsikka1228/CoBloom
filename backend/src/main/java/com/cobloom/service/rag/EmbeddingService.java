package com.cobloom.service.rag;

public interface EmbeddingService {
  double[] embed(String text);
  String embedToString(String text);
  double cosine(double[] embeddingA, double[] embeddingB);
  double cosine(double[] embeddingA, String embeddingB);
  double cosine(String embeddingA, String embeddingB);
}
