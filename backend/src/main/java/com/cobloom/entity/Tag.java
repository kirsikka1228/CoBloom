package com.cobloom.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;

@TableName("tag")
public class Tag {
  @TableId(type = IdType.AUTO)
  public Long id;
  public Long userId;
  public String name;
}
