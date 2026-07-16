package com.cobloom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("knowledge_node")
public class KnowledgeNode {
  @TableId(type = IdType.AUTO)
  public Long id;
  public Long userId;
  public String nodeType;
  public String name;
  public String normalizedName;
  public String description;
  public Long sourceRecordId;
  public LocalDateTime createdAt;
  public LocalDateTime updatedAt;
}
