package com.cobloom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("knowledge_relation")
public class KnowledgeRelation {
  @TableId(type = IdType.AUTO)
  public Long id;
  public Long userId;
  public Long sourceNodeId;
  public Long targetNodeId;
  public String relationType;
  public Double weight;
  public Double confidence;
  public String evidenceText;
  public Long sourceRecordId;
  public LocalDateTime createdAt;
}
