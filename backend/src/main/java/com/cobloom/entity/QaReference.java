package com.cobloom.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;

@TableName("qa_reference")
public class QaReference {
  @TableId(type = IdType.AUTO)
  public Long id;
  public Long qaRecordId;
  public Long recordId;
  public Long chunkId;
  public String snippet;
  public Double similarity;
}
