package com.usmind.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("oss_config")
public class OssConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String type;
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucket;
    private String region;
    private String domain;
    private Integer isActive;
    private String configJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
