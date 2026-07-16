package com.cobloom.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;

@TableName("record_tag")
public class RecordTag {
  @TableId(type = IdType.AUTO)
  public Long id;
  public Long recordId;
  public Long tagId;
}
