package com.cobloom.dto;

import java.util.List;

public class KnowledgeNodeDetailDTO {
  public GraphNodeDTO node;
  public List<SourceRecordDTO> sources;
  public List<GraphNodeDTO> neighbors;
  public List<RelatedQuestionDTO> relatedQuestions;

  public KnowledgeNodeDetailDTO(GraphNodeDTO node, List<SourceRecordDTO> sources,
                                List<GraphNodeDTO> neighbors, List<RelatedQuestionDTO> relatedQuestions) {
    this.node = node;
    this.sources = sources;
    this.neighbors = neighbors;
    this.relatedQuestions = relatedQuestions;
  }

  public static class SourceRecordDTO {
    public Long recordId;
    public String title;
    public String snippet;

    public SourceRecordDTO(Long recordId, String title, String snippet) {
      this.recordId = recordId;
      this.title = title;
      this.snippet = snippet;
    }
  }

  public static class RelatedQuestionDTO {
    public Long qaRecordId;
    public String question;

    public RelatedQuestionDTO(Long qaRecordId, String question) {
      this.qaRecordId = qaRecordId;
      this.question = question;
    }
  }
}
