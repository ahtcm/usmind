package com.usmind.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会员兑换码：一次性，可兑换指定时长/级别的会员。
 * 后台创建后用户端输入 code 兑换（POST /api/member/redeem）。
 */
@Data
@TableName("redeem_code")
public class RedeemCode {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 兑换码（随机大写字母数字，唯一） */
    private String code;
    /** 关联套餐 id（可空，空则用 days/level 自定义） */
    private Long planId;
    /** 兑换后会员时长（天） */
    private Integer days;
    /** 兑换后会员级别 */
    private Integer level;
    /** active 可用 / used 已兑换 / disabled 停用 */
    private String status;
    /** 已兑换用户 id */
    private Long usedBy;
    /** 已兑换时间 */
    private LocalDateTime usedAt;
    /** 备注 */
    private String remark;
    /** 创建人 */
    private Long createdBy;
    private LocalDateTime createdAt;
}
