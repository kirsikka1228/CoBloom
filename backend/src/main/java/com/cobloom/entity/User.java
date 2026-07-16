package com.cobloom.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import java.time.LocalDateTime;

@TableName("`user`")
public class User {
  @TableId(type = IdType.AUTO)
  public Long id;
  public String username;
  public String password;
  public String nickname;
  public LocalDateTime createdAt;
  public LocalDateTime updatedAt;
}
