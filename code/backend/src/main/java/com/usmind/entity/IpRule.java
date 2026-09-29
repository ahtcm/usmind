package com.usmind.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ip_rule")
public class IpRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String ruleType;
    private String targetType;
    private String targetValue;
    private String remark;
    private Integer enabled;
    private LocalDateTime createdAt;
}
