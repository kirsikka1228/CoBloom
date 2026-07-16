package com.cobloom.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import java.time.LocalDateTime;

@TableName("record")
public class GrowthRecord {
  @TableId(type = IdType.AUTO)
  public Long id;
  public Long userId;
  public String title;
  public String content;
  public String recordType;
  public String mood;
  public String summary;
  public String keywords;
  public String graphStatus;
  public String graphStage;
  public String graphError;
  public LocalDateTime graphUpdatedAt;
  public LocalDateTime createdAt;
  public LocalDateTime updatedAt;
}
