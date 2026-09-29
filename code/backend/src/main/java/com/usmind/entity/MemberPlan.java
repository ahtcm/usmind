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
    private Integer days;
    private Integer sort;
    private Integer enabled;
    private LocalDateTime createdAt;
}
