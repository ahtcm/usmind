package com.usmind.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("member_plan")
public class MemberPlan {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Integer level;
    private BigDecimal price;
    /** 原价（0=无原价，前端划线展示） */
    private BigDecimal originalPrice;
    /** 折扣百分比（0-100，如 80 表示 8 折；0=无折扣） */
    private Integer discountPercent;
    private Integer days;
    private Integer sort;
    private Integer enabled;
    private LocalDateTime createdAt;
}
