package com.usmind.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mind_map")
public class MindMap {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    private String data;
    private String layout;
    private String theme;
    private String viewData;
    private String manualStyles;
    private Integer isShared;
    private String shareToken;
    private String sharePassword;
    private Integer viewCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
