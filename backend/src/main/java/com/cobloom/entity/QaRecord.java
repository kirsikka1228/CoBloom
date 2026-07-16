package com.cobloom.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import java.time.LocalDateTime;

@TableName("qa_record")
public class QaRecord {
  @TableId(type = IdType.AUTO)
  public Long id;
  public Long userId;
  public String question;
  public String answer;
  public LocalDateTime createdAt;
}
