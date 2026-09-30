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
    /** 自动拉黑过期时间（超频自动拉黑写入 now+封禁时长；到期自动解封）。手动规则为 null=永久 */
    private LocalDateTime expireAt;
    private LocalDateTime createdAt;
}
