package com.usmind.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String nickname;
    private String avatar;
    private String email;
    private String phone;
    private String role;
    private Integer status;
    private Integer memberLevel;
    private java.time.LocalDateTime memberExpire;
    /** 会员来源：pay(支付)/redeem(兑换码)/admin(后台开通)，null=非会员 */
    private String memberSource;
    private String source;
    private String openidWechat;
    private String openidQq;
    private java.time.LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
