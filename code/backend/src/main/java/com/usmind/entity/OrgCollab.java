package com.usmind.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("org_collab")
public class OrgCollab {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orgId;
    private Long mindMapId;
    private Long ownerId;
    private LocalDateTime createdAt;
}
