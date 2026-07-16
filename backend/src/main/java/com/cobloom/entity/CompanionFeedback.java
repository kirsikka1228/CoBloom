package com.cobloom.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import java.time.LocalDateTime;

@TableName("companion_feedback")
public class CompanionFeedback {
  @TableId(type = IdType.AUTO)
  public Long id;
  public Long userId;
  public Long recordId;
  public String feedbackType;
  public String content;
  public LocalDateTime createdAt;
}
