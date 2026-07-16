package com.cobloom.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import java.time.LocalDateTime;

@TableName("record_chunk")
public class RecordChunk {
  @TableId(type = IdType.AUTO)
  public Long id;
  public Long userId;
  public Long recordId;
  public Integer chunkIndex;
  public String content;
  public String embedding;
  public LocalDateTime createdAt;
}
