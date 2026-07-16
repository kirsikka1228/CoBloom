package com.cobloom.dto;

public class QAReferenceDTO {
  public Long noteId;
  public String title;
  public String snippet;
  public double score;

  public Long recordId;
  public String recordTitle;
  public String chunkText;
  public double similarity;

  public QAReferenceDTO(Long noteId, String title, String snippet, double score) {
    this.noteId = noteId;
    this.title = title;
    this.snippet = snippet;
    this.score = score;
    this.recordId = noteId;
    this.recordTitle = title;
    this.chunkText = snippet;
    this.similarity = score;
  }
}
